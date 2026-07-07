package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkAdapter;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkMessage;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkProperties;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkResult;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushMessage;
import com.eeum.eeum.infrastructure.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 생성 메시지 실발송 디스패처.
 * - APP_PUSH → FCM(PushAdapter), KAKAO_ALERT → 알림톡 Adapter
 * - 메시지 유형별 대상 고객 조회 + 마케팅 수신 동의 필터
 * - 수신자별 결과를 AiMessageDelivery에 기록 (일부 실패가 전체 실패로 이어지지 않음)
 * - 메시지당 1회만 발송 (Redis 락 + delivery 존재 여부로 멱등)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageDispatchService {

    private static final Duration DISPATCH_LOCK_LEASE = Duration.ofSeconds(30);

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;
    private final AccountRepository accountRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final FavoriteRepository favoriteRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InquiryRepository inquiryRepository;
    private final PushAdapter pushAdapter;
    private final AlimtalkAdapter alimtalkAdapter;
    private final AlimtalkProperties alimtalkProperties;
    private final RedisLockService redisLockService;

    @Transactional
    public void dispatch(Long messageId) {
        redisLockService.executeWithLock(LockKeys.aiMessageDispatch(messageId), DISPATCH_LOCK_LEASE, () -> {
            AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId).orElse(null);
            if (message == null) {
                return;
            }
            // 멱등 — 이미 수신자 발송 기록이 있으면 재발송하지 않는다
            if (aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(messageId)) {
                log.info("[AI-DISPATCH] 이미 발송된 메시지 — 스킵: messageId={}", messageId);
                return;
            }
            AiChannel channel = message.getChannel();
            // STORE_NOTICE는 공지 등록으로 처리 완료, SNS_CARD는 문구 생성 전용
            if (channel != AiChannel.APP_PUSH && channel != AiChannel.KAKAO_ALERT) {
                return;
            }
            dispatchToTargets(message, channel);
        });
    }

    private void dispatchToTargets(AiGeneratedMessage message, AiChannel channel) {
        Store store = message.getStore();
        Set<Long> targetIds = resolveTargetAccountIds(message);
        targetIds.remove(message.getOwnerAccount().getAccountId());

        if (targetIds.isEmpty()) {
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, null, channel, AiDeliveryStatus.NO_TARGET, "발송 대상 없음", null));
            log.info("[AI-DISPATCH] 발송 대상 0명: messageId={}", message.getAiGeneratedMessageId());
            return;
        }

        // 고객에게 발송되는 메시지는 전부 마케팅성으로 간주 — 수신 동의 고객만 발송
        Set<Long> consented = new HashSet<>(notificationSettingsRepository
                .findMarketingEnabledAccountIds(List.copyOf(targetIds)));
        Map<Long, Account> accounts = accountRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));

        int success = 0;
        int failed = 0;
        for (Long accountId : targetIds) {
            Account account = accounts.get(accountId);
            if (account == null) {
                continue;
            }
            if (!consented.contains(accountId)) {
                aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                        message, store, accountId, channel, AiDeliveryStatus.SKIPPED_NO_CONSENT, null, null));
                continue;
            }
            // 개별 발송 실패가 전체 트랜잭션을 깨지 않도록 격리
            try {
                if (sendToAccount(message, store, account, channel)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("[AI-DISPATCH] 수신자 발송 중 오류: messageId={}, accountId={}",
                        message.getAiGeneratedMessageId(), accountId, e);
                aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                        message, store, accountId, channel, AiDeliveryStatus.FAILED,
                        e.getClass().getSimpleName(), null));
            }
        }
        log.info("[AI-DISPATCH] 발송 완료: messageId={}, channel={}, 성공={}, 실패={}",
                message.getAiGeneratedMessageId(), channel, success, failed);
    }

    // 발송 성공 시 true, 실패/스킵 시 false
    private boolean sendToAccount(AiGeneratedMessage message, Store store, Account account, AiChannel channel) {
        if (channel == AiChannel.APP_PUSH) {
            if (account.getFcmToken() == null || account.getFcmToken().isBlank()) {
                aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                        message, store, account.getAccountId(), channel,
                        AiDeliveryStatus.SKIPPED_NO_TOKEN, null, null));
                return false;
            }
            PushResult result = pushAdapter.send(PushMessage.builder()
                    .fcmToken(account.getFcmToken())
                    .title(message.getTitle() != null ? message.getTitle() : store.getName())
                    .body(message.getContent())
                    .linkUrl("/stores/" + store.getStoreId())
                    .build());
            if (result.isInvalidToken()) {
                // 기존 FCM 무효 토큰 정책 — 재발송 방지를 위해 토큰 무효화
                account.updateFcmToken(null);
            }
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, account.getAccountId(), channel,
                    result.isSuccess() ? AiDeliveryStatus.SENT : AiDeliveryStatus.FAILED,
                    result.isSuccess() ? null : result.getErrorCode(),
                    result.getMessageId()));
            return result.isSuccess();
        }

        // KAKAO_ALERT — 승인된 템플릿 코드 기반 발송
        AlimtalkResult result = alimtalkAdapter.send(new AlimtalkMessage(
                account.getPhone(),
                resolveTemplateCode(message),
                message.getTitle(),
                message.getContent()));
        aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                message, store, account.getAccountId(), channel,
                result.success() ? AiDeliveryStatus.SENT : AiDeliveryStatus.FAILED,
                result.success() ? null : result.errorCode(),
                result.providerMessageId()));
        return result.success();
    }

    private String resolveTemplateCode(AiGeneratedMessage message) {
        AlimtalkProperties.Templates templates = alimtalkProperties.templates();
        return switch (message.getType()) {
            case CUSTOMER_CARE -> templates.customerCare();
            case EVENT_MARKETING -> templates.marketing();
            default -> templates.notice();
        };
    }

    // ===================== 발송 대상 조회 =====================

    private Set<Long> resolveTargetAccountIds(AiGeneratedMessage message) {
        Long storeId = message.getStore().getStoreId();
        return switch (message.getType()) {
            case CUSTOMER_CARE -> resolveCareTargets(message, storeId);
            // 마케팅/공지 — 찜 고객 + 최근 주문 고객 (동의 필터는 이후 단계에서 적용)
            case EVENT_MARKETING, NOTICE -> {
                Set<Long> ids = new LinkedHashSet<>(
                        favoriteRepository.findAccountIdsByRefTypeAndRefId(FavoriteRefType.STORE, storeId));
                ids.addAll(orderRepository.findOrderAccountIdsByStoreIdAndStatus(storeId, OrderStatus.COMPLETED));
                yield ids;
            }
            // 답글/문의 답변 등은 고객 푸시 대상이 아님
            default -> new LinkedHashSet<>();
        };
    }

    private Set<Long> resolveCareTargets(AiGeneratedMessage message, Long storeId) {
        if (message.hasCareType(AiCareType.CART_INTEREST)) {
            return cartRepository.findByStore_StoreId(storeId).stream()
                    .map(cart -> cart.getAccount().getAccountId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (message.hasCareType(AiCareType.INQUIRY_HESITATION)) {
            return inquiryRepository
                    .findByStore_StoreIdAndStatusOrderByCreatedAtDesc(storeId, InquiryStatus.PENDING).stream()
                    .map(inquiry -> inquiry.getWriter().getAccountId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // INACTIVE_REGULAR — 완료 주문 3건 이상이면서 최근 30일 주문이 없는 단골
        List<Order> completedOrders = orderRepository.findByStore_StoreIdAndStatus(storeId, OrderStatus.COMPLETED);
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        Map<Long, List<Order>> byAccount = completedOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getAccount().getAccountId()));
        return byAccount.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 3)
                .filter(entry -> entry.getValue().stream()
                        .map(Order::getCreatedAt)
                        .max(LocalDateTime::compareTo)
                        .map(last -> last.isBefore(threshold))
                        .orElse(false))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
