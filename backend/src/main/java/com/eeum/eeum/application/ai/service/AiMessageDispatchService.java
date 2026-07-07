package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
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
 * - 실제 채널 발송과 별개로, 앱/웹 알림함에 남도록 Notification도 함께 생성 (FCM 재발송 없이 createNotificationWithoutPush 사용)
 * - 메시지당 1회만 발송 (Redis 락 + delivery 존재 여부로 멱등)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageDispatchService {

    private static final Duration DISPATCH_LOCK_LEASE = Duration.ofSeconds(30);
    private static final int NOTIFICATION_CONTENT_MAX_LENGTH = 150;

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;
    private final AccountRepository accountRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationService notificationService;
    private final FavoriteRepository favoriteRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InquiryRepository inquiryRepository;
    private final StoreReviewRepository storeReviewRepository;
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

        // INQUIRY_REPLY/REVIEW_REPLY는 sendInTx 단계에서 이미 도메인 이벤트(InquiryAnsweredEvent/
        // StoreReviewReplyCreatedEvent)로 알림이 발송됐다 — 여기서 재발송하지 않고
        // 정확한 target_account_id로 발송 이력만 남긴다 (중복 푸시 방지).
        if (isDirectReplyType(message.getType())) {
            recordDirectReplyDelivery(message, store, channel);
            return;
        }

        Set<Long> targetIds = resolveTargetAccountIds(message);
        targetIds.remove(message.getOwnerAccount().getAccountId());

        if (targetIds.isEmpty()) {
            String reason = noTargetReason(message.getType());
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, null, channel, AiDeliveryStatus.NO_TARGET, reason, null));
            log.info("[AI-DISPATCH] 발송 대상 0명: messageId={}, type={}, reason={}",
                    message.getAiGeneratedMessageId(), message.getType(), reason);
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
        // 실제 채널(FCM/알림톡) 발송 성패와 무관하게 앱/웹 알림함에는 남긴다 — FCM 재발송은 하지 않는다(중복 푸시 방지)
        createLinkedNotification(message, store, account);

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

    // 알림함(Notification)에 남기기 위한 기록 — 이미 marketingEnabled 동의 필터를 통과한 대상만 호출된다.
    // FCM 재발송은 하지 않는다(triggerPush=false) — 실제 채널 발송은 이 메서드 호출 직후 별도로 처리된다.
    private void createLinkedNotification(AiGeneratedMessage message, Store store, Account account) {
        NotificationType notificationType = resolveNotificationType(message.getType());
        if (notificationType == null) {
            return;
        }
        notificationService.createNotificationWithoutPush(NotificationCreateRequestDto.builder()
                .accountId(account.getAccountId())
                .type(notificationType)
                .title(message.getTitle() != null ? message.getTitle() : store.getName() + " 소식")
                .content(truncate(message.getContent(), NOTIFICATION_CONTENT_MAX_LENGTH))
                .refType(NotificationRefType.STORE)
                .refId(store.getStoreId())
                .linkUrl("/stores/" + store.getStoreId())
                .build());
    }

    // CUSTOMER_CARE/EVENT_MARKETING/NOTICE만 여기 도달 — 세분화된 NotificationType이 따로 없어
    // 기존 MARKETING_EVENT(마케팅 수신 동의로 게이팅)를 그대로 재사용한다.
    private NotificationType resolveNotificationType(AiMessageType type) {
        return switch (type) {
            case CUSTOMER_CARE, EVENT_MARKETING, NOTICE -> NotificationType.MARKETING_EVENT;
            default -> null;
        };
    }

    private String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
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

    // INQUIRY_REPLY/REVIEW_REPLY는 dispatchToTargets 상단에서 recordDirectReplyDelivery로 먼저 분기되므로
    // 이 메서드까지 내려오지 않는다 — 여기 도달하는 타입은 다중/불특정 대상 타입뿐이다.
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
            // COMPLAINT_REPLY — 반복 불만 키워드 기반 대응 문구로 특정 개별 수신자가 없음
            default -> new LinkedHashSet<>();
        };
    }

    // 타입별 "발송 대상 없음" 사유 — NO_TARGET이 버그인지 정상 정책인지 로그/DB에서 구분 가능하도록 구체화
    private String noTargetReason(AiMessageType type) {
        return switch (type) {
            case CUSTOMER_CARE -> "고객 케어 대상 없음";
            case EVENT_MARKETING -> "마케팅 수신 대상 없음";
            case NOTICE -> "공지 발송 대상 없음";
            case COMPLAINT_REPLY -> "반복 불만 대응 문구는 개별 수신자 없음";
            default -> "발송 대상 없음";
        };
    }

    private boolean isDirectReplyType(AiMessageType type) {
        return type == AiMessageType.INQUIRY_REPLY || type == AiMessageType.REVIEW_REPLY;
    }

    // INQUIRY_REPLY/REVIEW_REPLY 발송 이력 — 실제 알림은 sendInTx에서 이미 나갔으므로 재발송 없이 기록만 남긴다
    private void recordDirectReplyDelivery(AiGeneratedMessage message, Store store, AiChannel channel) {
        Long targetAccountId = resolveDirectReplyTargetAccountId(message);
        if (targetAccountId == null) {
            String reason = message.getType() == AiMessageType.INQUIRY_REPLY
                    ? "문의 작성자 계정 없음"
                    : "리뷰 작성자 계정 없음";
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, null, channel, AiDeliveryStatus.NO_TARGET, reason, null));
            log.info("[AI-DISPATCH] 발송 대상 없음: messageId={}, type={}, targetType={}, targetId={}, reason={}",
                    message.getAiGeneratedMessageId(), message.getType(), message.getTargetType(),
                    message.getTargetId(), reason);
            return;
        }
        aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                message, store, targetAccountId, channel, AiDeliveryStatus.SENT, null, null));
        log.info("[AI-DISPATCH] 발송 완료(답변 알림 채널 경유): messageId={}, type={}, targetType={}, targetId={}, targetAccountId={}",
                message.getAiGeneratedMessageId(), message.getType(), message.getTargetType(),
                message.getTargetId(), targetAccountId);
    }

    // 문의 작성자 / 리뷰 작성자 accountId 조회 — store 범위로 재검증해 다른 매장 대상 조작을 차단
    private Long resolveDirectReplyTargetAccountId(AiGeneratedMessage message) {
        Long storeId = message.getStore().getStoreId();
        return switch (message.getType()) {
            case INQUIRY_REPLY -> inquiryRepository
                    .findByInquiryIdAndStore_StoreId(message.getTargetId(), storeId)
                    .map(inquiry -> inquiry.getWriter().getAccountId())
                    .orElse(null);
            case REVIEW_REPLY -> storeReviewRepository
                    .findByStorereviewIdAndStore_StoreId(message.getTargetId(), storeId)
                    .map(review -> review.getAccount().getAccountId())
                    .orElse(null);
            default -> null;
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
