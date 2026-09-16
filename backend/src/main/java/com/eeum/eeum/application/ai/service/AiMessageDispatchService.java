package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.AccountSendInfo;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DeliveryOutcome;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DispatchClaim;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 생성 메시지 실발송 디스패처.
 * - APP_PUSH → FCM(PushAdapter), KAKAO_ALERT → 알림톡 Adapter
 * - 발송 대상 확정(AiMessageDispatchClaimExecutor)과 결과 기록은 짧은 트랜잭션으로 분리하고,
 *   실제 채널 발송(외부 HTTP 호출)은 트랜잭션 밖에서 수행한다 — DB 커넥션을 오래 붙잡지 않기 위함.
 * - 락(aiMessageDispatch)은 확정~발송~기록 전체를 감싸 멱등을 보장하되, 락 내부에 DB 트랜잭션을
 *   열어둔 채로 커밋 전에 락이 풀리는 일이 없도록 각 단계가 스스로 커밋까지 마친다.
 * - 앱/웹 알림함에는 채널 발송 성패와 무관하게 남도록 Notification도 함께 생성한다(FCM 재발송 없이).
 * - DND(방해 금지) 활성 시 APP_PUSH는 스킵하되 인앱 알림함 기록은 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageDispatchService {

    private static final Duration DISPATCH_LOCK_LEASE = Duration.ofSeconds(30);
    private static final int NOTIFICATION_CONTENT_MAX_LENGTH = 150;

    private final AiMessageDispatchClaimExecutor claimExecutor;
    private final NotificationService notificationService;
    private final PushAdapter pushAdapter;
    private final AlimtalkAdapter alimtalkAdapter;
    private final AlimtalkProperties alimtalkProperties;
    private final RedisLockService redisLockService;

    public void dispatch(Long messageId) {
        // 상태 전이 락(aiMessage)과 동일 키를 사용해 사장의 취소(cancelMessage)와 상호 배제한다.
        // 별도 키(aiMessageDispatch)를 쓰면 예약 메시지가 dispatch 중일 때 취소가 통과해, 취소 응답을
        // 받은 메시지가 실제로는 발송되는 문제가 생긴다.
        redisLockService.executeWithLock(LockKeys.aiMessage(messageId), DISPATCH_LOCK_LEASE, () -> {
            DispatchClaim claim = claimExecutor.claimInTx(messageId);
            if (!claim.shouldSendToTargets()) {
                return;
            }

            // 외부 채널 호출은 DB 트랜잭션 밖에서 수행 — 커넥션을 붙잡지 않는다
            List<DeliveryOutcome> outcomes = new ArrayList<>();
            for (AccountSendInfo target : claim.targets()) {
                outcomes.add(sendToAccount(claim, target));
            }

            claimExecutor.recordOutcomesInTx(messageId, outcomes);
        });
    }

    // 발송 성공/실패/스킵 결과를 값 타입으로 반환 — DB 기록은 이 메서드가 아니라 recordOutcomesInTx가 배치로 처리
    private DeliveryOutcome sendToAccount(DispatchClaim claim, AccountSendInfo target) {
        // 실제 채널(FCM/알림톡) 발송 성패와 무관하게 앱/웹 알림함에는 남긴다 — FCM 재발송은 하지 않는다(중복 푸시 방지)
        createLinkedNotification(claim, target.accountId());

        if (claim.channel() == AiChannel.APP_PUSH) {
            if (target.fcmToken() == null || target.fcmToken().isBlank()) {
                return new DeliveryOutcome(target.accountId(), claim.channel(),
                        AiDeliveryStatus.SKIPPED_NO_TOKEN, null, null, false);
            }
            if (target.dndActive()) {
                log.debug("[AI-DISPATCH] DND 활성 — 푸시 스킵: accountId={}", target.accountId());
                return new DeliveryOutcome(target.accountId(), claim.channel(),
                        AiDeliveryStatus.SKIPPED_DND, null, null, false);
            }
            PushResult result = pushAdapter.send(PushMessage.builder()
                    .fcmToken(target.fcmToken())
                    .title(claim.messageTitle() != null ? claim.messageTitle() : claim.storeName())
                    .body(claim.messageContent())
                    .linkUrl("/stores/" + claim.storeId())
                    .build());
            return new DeliveryOutcome(target.accountId(), claim.channel(),
                    result.isSuccess() ? AiDeliveryStatus.SENT : AiDeliveryStatus.FAILED,
                    result.isSuccess() ? null : result.getErrorCode(),
                    result.getMessageId(),
                    result.isInvalidToken());
        }

        // KAKAO_ALERT — 승인된 템플릿 코드 기반 발송
        AlimtalkResult result = alimtalkAdapter.send(new AlimtalkMessage(
                target.phone(),
                resolveTemplateCode(claim.messageType()),
                claim.messageTitle(),
                claim.messageContent()));
        return new DeliveryOutcome(target.accountId(), claim.channel(),
                result.success() ? AiDeliveryStatus.SENT : AiDeliveryStatus.FAILED,
                result.success() ? null : result.errorCode(),
                result.providerMessageId(),
                false);
    }

    // 알림함(Notification)에 남기기 위한 기록 — 이미 marketingEnabled 동의 필터를 통과한 대상만 호출된다.
    // FCM 재발송은 하지 않는다(triggerPush=false) — 실제 채널 발송은 이 메서드 호출 직후 별도로 처리된다.
    // NotificationService.createNotificationWithoutPush는 그 자체로 @Transactional이라 여기서 별도 TX 없이도 커밋된다.
    private void createLinkedNotification(DispatchClaim claim, Long accountId) {
        NotificationType notificationType = resolveNotificationType(claim.messageType());
        if (notificationType == null) {
            return;
        }
        notificationService.createNotificationWithoutPush(NotificationCreateRequestDto.builder()
                .accountId(accountId)
                .type(notificationType)
                .title(claim.messageTitle() != null ? claim.messageTitle() : claim.storeName() + " 소식")
                .content(truncate(claim.messageContent(), NOTIFICATION_CONTENT_MAX_LENGTH))
                .refType(NotificationRefType.STORE)
                .refId(claim.storeId())
                .linkUrl("/stores/" + claim.storeId())
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

    private String resolveTemplateCode(AiMessageType type) {
        AlimtalkProperties.Templates templates = alimtalkProperties.templates();
        return switch (type) {
            case CUSTOMER_CARE -> templates.customerCare();
            case EVENT_MARKETING -> templates.marketing();
            default -> templates.notice();
        };
    }
}
