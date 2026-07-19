package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AiMessageDispatchService의 "발송 대상 확정" 단계 전담 — 전부 DB 조회/기록만 수행하고 외부 채널(FCM/알림톡) 호출은
 * 하지 않는다. 짧은 트랜잭션으로 커밋까지 마친 뒤 락이 풀려야 하므로 별도 빈으로 분리했다.
 * 반환하는 DispatchClaim/AccountSendInfo는 트랜잭션 밖(외부 호출 구간)에서도 안전하게 쓰도록 엔티티가 아닌
 * 순수 값만 담는다 — detached 엔티티의 LazyInitializationException을 원천 차단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageDispatchClaimExecutor {

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;
    private final AccountRepository accountRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final FavoriteRepository favoriteRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InquiryRepository inquiryRepository;
    private final StoreReviewRepository storeReviewRepository;

    @Transactional
    public DispatchClaim claimInTx(Long messageId) {
        AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return DispatchClaim.none();
        }
        // 멱등 — 이미 수신자 발송 기록이 있으면 재발송하지 않는다
        if (aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(messageId)) {
            log.info("[AI-DISPATCH] 이미 발송된 메시지 — 스킵: messageId={}", messageId);
            return DispatchClaim.none();
        }
        // 취소/재검토로 상태가 바뀐 메시지는 발송하지 않는다 — 즉시발송 경로는 커밋된 SENT 상태로,
        // 예약발송 경로는 아직 SCHEDULED 상태(성공 후에만 스케줄러가 SENT로 전이)로 이 메서드에 도달한다.
        if (message.getStatus() != AiMessageStatus.SENT && message.getStatus() != AiMessageStatus.SCHEDULED) {
            log.info("[AI-DISPATCH] 발송 시점 상태 불일치로 스킵: messageId={}, status={}", messageId, message.getStatus());
            return DispatchClaim.none();
        }
        AiChannel channel = message.getChannel();
        // STORE_NOTICE는 공지 등록으로 처리 완료, SNS_CARD는 문구 생성 전용
        if (channel != AiChannel.APP_PUSH && channel != AiChannel.KAKAO_ALERT) {
            return DispatchClaim.none();
        }

        Store store = message.getStore();

        // INQUIRY_REPLY/REVIEW_REPLY는 sendInTx 단계에서 이미 도메인 이벤트로 알림이 발송됐다 — 재발송 없이 기록만 남긴다.
        if (isDirectReplyType(message.getType())) {
            recordDirectReplyDelivery(message, store, channel);
            return DispatchClaim.none();
        }

        Set<Long> targetIds = resolveTargetAccountIds(message);
        targetIds.remove(message.getOwnerAccount().getAccountId());

        if (targetIds.isEmpty()) {
            String reason = noTargetReason(message.getType());
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, null, channel, AiDeliveryStatus.NO_TARGET, reason, null));
            log.info("[AI-DISPATCH] 발송 대상 0명: messageId={}, type={}, reason={}", messageId, message.getType(), reason);
            return DispatchClaim.none();
        }

        // 고객에게 발송되는 메시지는 전부 마케팅성으로 간주 — 수신 동의 고객만 발송
        Set<Long> consented = new HashSet<>(notificationSettingsRepository
                .findMarketingEnabledAccountIds(List.copyOf(targetIds)));
        Map<Long, Account> accounts = accountRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));
        Map<Long, NotificationSettings> settingsByAccount = notificationSettingsRepository
                .findByAccount_AccountIdIn(List.copyOf(targetIds)).stream()
                .collect(Collectors.toMap(settings -> settings.getAccount().getAccountId(), Function.identity()));

        List<AccountSendInfo> targets = new java.util.ArrayList<>();
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
            boolean dndActive = settingsByAccount.containsKey(accountId)
                    && settingsByAccount.get(accountId).isDndActive();
            targets.add(new AccountSendInfo(accountId, account.getFcmToken(), account.getPhone(), dndActive));
        }

        return new DispatchClaim(
                !targets.isEmpty(), messageId, store.getStoreId(), store.getName(),
                message.getType(), channel, message.getTitle(), message.getContent(), targets);
    }

    // 발송 성공/실패 결과를 배치로 기록 — 실제 채널 발송(외부 호출)이 전부 끝난 뒤 한 번에 호출된다.
    // 메시지/스토어는 이 시점에 한 번만 재조회하고(엔티티를 트랜잭션 경계 밖으로 들고 다니지 않음), FK 참조로만 사용한다.
    @Transactional
    public void recordOutcomesInTx(Long messageId, List<DeliveryOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return;
        }
        AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId).orElse(null);
        if (message == null) {
            log.warn("[AI-DISPATCH] 결과 기록 시점에 메시지가 존재하지 않음: messageId={}", messageId);
            return;
        }
        Store store = message.getStore();

        Set<Long> invalidTokenAccountIds = outcomes.stream()
                .filter(DeliveryOutcome::invalidToken)
                .map(DeliveryOutcome::accountId)
                .collect(Collectors.toSet());
        if (!invalidTokenAccountIds.isEmpty()) {
            accountRepository.findAllById(invalidTokenAccountIds)
                    .forEach(account -> account.updateFcmToken(null));
        }

        int success = 0;
        int failed = 0;
        for (DeliveryOutcome outcome : outcomes) {
            aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                    message, store, outcome.accountId(), outcome.channel(),
                    outcome.status(), outcome.failedReason(), outcome.providerMessageId()));
            if (outcome.status() == AiDeliveryStatus.SENT) {
                success++;
            } else if (outcome.status() == AiDeliveryStatus.FAILED) {
                failed++;
            }
        }
        log.info("[AI-DISPATCH] 발송 완료: messageId={}, 성공={}, 실패={}, 전체={}",
                messageId, success, failed, outcomes.size());
    }

    // ===================== 발송 대상 조회 =====================

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

    // INQUIRY_REPLY/REVIEW_REPLY는 recordDirectReplyDelivery로 먼저 분기되므로 여기까지 내려오지 않는다
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

    // ===================== 값 타입 (엔티티 아님 — TX 경계를 넘어 안전하게 전달) =====================

    public record DispatchClaim(
            boolean shouldSendToTargets,
            Long messageId,
            Long storeId,
            String storeName,
            AiMessageType messageType,
            AiChannel channel,
            String messageTitle,
            String messageContent,
            List<AccountSendInfo> targets
    ) {
        public static DispatchClaim none() {
            return new DispatchClaim(false, null, null, null, null, null, null, null, List.of());
        }
    }

    public record AccountSendInfo(Long accountId, String fcmToken, String phone, boolean dndActive) {
    }

    public record DeliveryOutcome(
            Long accountId,
            AiChannel channel,
            AiDeliveryStatus status,
            String failedReason,
            String providerMessageId,
            boolean invalidToken
    ) {
    }
}
