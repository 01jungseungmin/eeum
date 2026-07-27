package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
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

/**
 * AI 플랜 구독/결제 연동.
 * - 결제 성공 전에는 플랜 변경 금지 (PortOne 결제 검증 후에만 구독 반영)
 * - Webhook/검증 완료는 Redis 락 + 상태 체크로 멱등 처리
 * - 구독 취소: 기본은 결제 기간 종료일까지 유지 (ai.plan.cancel-immediately=true면 즉시 FREE)
 *
 * 락-트랜잭션 순서 주의: 이 클래스의 공개 메서드(requestSubscription/completePayment/handleWebhook)는
 * 그 자체로 @Transactional을 걸지 않는다 — 락을 먼저 잡고, DB 쓰기는 AiPlanPaymentCommandExecutor의
 * @Transactional 메서드가 커밋까지 마친 뒤에 락을 해제해야 하기 때문 (AiMessageCommandExecutor와 동일 패턴).
 * 과거에는 락이 @Transactional 메서드 "안"에서 걸렸다 풀려 커밋 전에 락이 풀리는 틈이 있었고,
 * 그 틈에 Webhook과 클라이언트 리다이렉트가 동시 진입하면 같은 결제에 대해 활성 구독이 2개 생성될 수 있었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanSubscriptionService {

    private static final Duration PAYMENT_LOCK_LEASE = Duration.ofSeconds(10);
    private static final Duration REQUEST_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final AiPlanPaymentRepository aiPlanPaymentRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final AiPlanPaymentCommandExecutor paymentCommandExecutor;
    private final RedisLockService redisLockService;

    // 구독 취소 시 즉시 FREE로 내릴지 (기본: 결제 기간 종료일까지 유지)
    @Value("${ai.plan.cancel-immediately:false}")
    private boolean cancelImmediately;

    // 구독 결제 요청 생성 — 프론트가 반환된 paymentId로 PortOne 결제창을 연다.
    // store 단위 락으로 "이미 같은 플랜 활성 구독 여부" 체크~PENDING 결제 생성을 원자적으로 묶어
    // 더블클릭/멀티탭으로 인한 중복 결제 요청을 막는다 (Executor가 커밋한 뒤에만 락 해제).
    public AiPlanSubscribeResponseDto requestSubscription(Long ownerId, AiPlanSubscribeRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        AiPlanType planType = request.getPlanType();
        if (planType == null || planType == AiPlanType.FREE) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "구독할 플랜은 BASIC 또는 PRO여야 합니다");
        }

        AiPlanPayment payment = redisLockService.executeWithLock(
                LockKeys.aiPlanSubscriptionRequest(store.getStoreId()),
                REQUEST_LOCK_LEASE,
                () -> paymentCommandExecutor.requestSubscriptionInTx(store, planType));
        return new AiPlanSubscribeResponseDto(
                payment.getPortonePaymentId(), planType, payment.getAmount(), payment.getStatus());
    }

    // 결제 완료 검증 (클라이언트 리다이렉트 경로) — 타 사장 결제 접근 차단.
    // 소유권 확인은 읽기 전용이라 락/TX 없이 진행하고, 실제 반영은 applyPaidSubscription에 위임한다.
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
    public void handleWebhook(String paymentId) {
        if (aiPlanPaymentRepository.findByPortonePaymentId(paymentId).isEmpty()) {
            log.warn("[AI-PLAN] 등록되지 않은 AI 플랜 결제 Webhook: 무시");
            return;
        }
        applyPaidSubscription(paymentId);
    }

    // 결제 검증 + 구독 반영 — 락을 먼저 잡고, Executor의 @Transactional 메서드가 커밋을 마친 뒤에만 락을 해제한다.
    // Webhook과 completePayment가 동시에 들어와도 같은 paymentId 락으로 직렬화되고, 두 번째 진입은
    // 첫 번째가 커밋한 최신 상태(PAID)를 읽으므로 중복 구독 생성 없이 멱등 스킵/복구 경로로만 흐른다.
    private void applyPaidSubscription(String paymentId) {
        redisLockService.executeWithLock(
                LockKeys.aiPlanPayment(paymentId),
                PAYMENT_LOCK_LEASE,
                () -> paymentCommandExecutor.applyPaidSubscriptionInTx(paymentId));
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
