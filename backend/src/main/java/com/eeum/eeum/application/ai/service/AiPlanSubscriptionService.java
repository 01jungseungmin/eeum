package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 플랜 구독/결제 연동.
 * - 결제 성공 전에는 플랜 변경 금지 (PortOne 결제 검증 후에만 구독 반영)
 * - Webhook/검증 완료는 Redis 락 + 상태 체크로 멱등 처리
 * - 구독 취소: 기본은 결제 기간 종료일까지 유지 (ai.plan.cancel-immediately=true면 즉시 FREE)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanSubscriptionService {

    public static final String AI_PLAN_PAYMENT_PREFIX = "ai-plan-";
    private static final Duration PAYMENT_LOCK_LEASE = Duration.ofSeconds(10);
    private static final int SUBSCRIPTION_PERIOD_MONTHS = 1;

    private final AiManagerSupportService supportService;
    private final AiPlanPaymentRepository aiPlanPaymentRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final PortOnePaymentClient portOnePaymentClient;
    private final RedisLockService redisLockService;

    // 구독 취소 시 즉시 FREE로 내릴지 (기본: 결제 기간 종료일까지 유지)
    @Value("${ai.plan.cancel-immediately:false}")
    private boolean cancelImmediately;

    // 구독 결제 요청 생성 — 프론트가 반환된 paymentId로 PortOne 결제창을 연다
    @Transactional
    public AiPlanSubscribeResponseDto requestSubscription(Long ownerId, AiPlanSubscribeRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanType planType = request.getPlanType();
        if (planType == null || planType == AiPlanType.FREE) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "구독할 플랜은 BASIC 또는 PRO여야 합니다");
        }
        String paymentId = AI_PLAN_PAYMENT_PREFIX + store.getStoreId() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        AiPlanPayment payment = aiPlanPaymentRepository.save(
                AiPlanPayment.createPending(store, planType, planType.getMonthlyPrice(), paymentId));
        return new AiPlanSubscribeResponseDto(
                payment.getPortonePaymentId(), planType, payment.getAmount(), payment.getStatus());
    }

    // 결제 완료 검증 (클라이언트 리다이렉트 경로) — 타 사장 결제 접근 차단
    @Transactional
    public AiPlanSubscribeResponseDto completePayment(Long ownerId, String paymentId) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.AI_FORBIDDEN);
        }
        applyPaidSubscription(paymentId);
        AiPlanPayment refreshed = aiPlanPaymentRepository.findByPortonePaymentId(paymentId).orElseThrow();
        return new AiPlanSubscribeResponseDto(
                paymentId, refreshed.getPlanType(), refreshed.getAmount(), refreshed.getStatus());
    }

    // Webhook 경로 — PaymentService가 ai-plan- prefix 결제를 위임
    @Transactional
    public void handleWebhook(String paymentId) {
        if (aiPlanPaymentRepository.findByPortonePaymentId(paymentId).isEmpty()) {
            log.warn("[AI-PLAN] 등록되지 않은 AI 플랜 결제 Webhook: 무시");
            return;
        }
        applyPaidSubscription(paymentId);
    }

    // 결제 검증 + 구독 반영 — 락 + 상태 체크 멱등 (중복 Webhook/중복 클릭 1회만 반영)
    private void applyPaidSubscription(String paymentId) {
        redisLockService.executeWithLock(LockKeys.aiPlanPayment(paymentId), PAYMENT_LOCK_LEASE, () -> {
            AiPlanPayment payment = aiPlanPaymentRepository.findByPortonePaymentId(paymentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
            if (payment.isPaid()) {
                log.info("[AI-PLAN] 이미 반영된 결제 — 멱등 스킵");
                return;
            }

            // PortOne 결제 검증 — 금액/상태 불일치 시 플랜 변경 없음
            PortOnePaymentInfo info = portOnePaymentClient.getPayment(paymentId);
            if (!"PAID".equalsIgnoreCase(info.getStatus())) {
                log.warn("[AI-PLAN] 결제 완료 상태가 아님: status={}", info.getStatus());
                throw new BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED);
            }
            if (info.getAmount() == null || payment.getAmount().compareTo(info.getAmount()) != 0) {
                payment.markFailed();
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }

            LocalDateTime now = LocalDateTime.now();
            payment.markPaid(now);

            // 기존 활성 구독 비활성화 후 새 구독 생성 (업그레이드/다운그레이드 즉시 반영)
            aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(payment.getStore().getStoreId())
                    .forEach(subscription -> subscription.deactivate(now));
            aiPlanSubscriptionRepository.save(AiPlanSubscription.createWithPeriod(
                    payment.getStore(), payment.getPlanType(), now, now.plusMonths(SUBSCRIPTION_PERIOD_MONTHS)));
            log.info("[AI-PLAN] 구독 반영 완료: storeId={}, plan={}",
                    payment.getStore().getStoreId(), payment.getPlanType());
        });
    }

    // 구독 취소 — 정책값에 따라 즉시 FREE 또는 기간 종료까지 유지
    @Transactional
    public void cancelSubscription(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanSubscription subscription = aiPlanSubscriptionRepository
                .findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_INVALID_STATUS, "활성 구독이 없습니다"));

        if (cancelImmediately) {
            subscription.deactivate(LocalDateTime.now());
            log.info("[AI-PLAN] 구독 즉시 취소: storeId={}", store.getStoreId());
        } else {
            // 기간 종료일(expiredAt)까지 유지 — 만료 스케줄러가 비활성화
            log.info("[AI-PLAN] 구독 취소 접수 — 기간 종료일까지 유지: storeId={}, expiredAt={}",
                    store.getStoreId(), subscription.getExpiredAt());
        }
    }
}
