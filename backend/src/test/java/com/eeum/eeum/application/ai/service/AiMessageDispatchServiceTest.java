package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.AccountSendInfo;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DeliveryOutcome;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DispatchClaim;
import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkAdapter;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkProperties;
import com.eeum.eeum.infrastructure.alimtalk.AlimtalkResult;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이 클래스는 순수 오케스트레이션(락 위임 + 외부 채널 호출 + 알림함 생성)만 다룬다.
 * 발송 대상 확정(consent/DND/타입별 대상 조회)과 결과 기록 로직은
 * AiMessageDispatchClaimExecutorTest에서 검증한다 (락-TX 분리 리팩터링으로 로직이 그쪽으로 이동함).
 */
@ExtendWith(MockitoExtension.class)
class AiMessageDispatchServiceTest {

    @InjectMocks
    private AiMessageDispatchService dispatchService;

    @Mock private AiMessageDispatchClaimExecutor claimExecutor;
    @Mock private NotificationService notificationService;
    @Mock private PushAdapter pushAdapter;
    @Mock private AlimtalkAdapter alimtalkAdapter;
    @Mock private RedisLockService redisLockService;
    @Spy private AlimtalkProperties alimtalkProperties = new AlimtalkProperties(
            "mock", null, null, null, null,
            new AlimtalkProperties.Templates("CARE_01", "MKT_01", "NOTICE_01"));

    private static final Long MESSAGE_ID = 1L;
    private static final Long STORE_ID = 1L;
    private static final Long CUSTOMER_ID = 10L;

    private void stubLockPassThrough() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));
    }

    private DispatchClaim claimWithTarget(AiChannel channel, AiMessageType type, AccountSendInfo target) {
        return new DispatchClaim(true, MESSAGE_ID, STORE_ID, "테스트 상점", type, channel, "제목", "본문", List.of(target));
    }

    @SuppressWarnings("unchecked")
    private List<DeliveryOutcome> capturedOutcomes() {
        ArgumentCaptor<List<DeliveryOutcome>> captor = ArgumentCaptor.forClass(List.class);
        verify(claimExecutor).recordOutcomesInTx(eq(MESSAGE_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void 발송_대상이_없으면_외부_채널_호출도_결과_기록도_일어나지_않는다() {
        // given
        stubLockPassThrough();
        when(claimExecutor.claimInTx(MESSAGE_ID)).thenReturn(DispatchClaim.none());

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        verify(alimtalkAdapter, never()).send(any());
        verify(claimExecutor, never()).recordOutcomesInTx(anyLong(), any());
    }

    @Test
    void APP_PUSH_대상이_있으면_FCM이_호출되고_SENT_결과가_기록되고_알림함에도_생성된다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "valid-token", "010-1234-5678", false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING, target));
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter).send(any());
        List<DeliveryOutcome> outcomes = capturedOutcomes();
        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).status()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(outcomes.get(0).providerMessageId()).isEqualTo("fcm-id");

        ArgumentCaptor<NotificationCreateRequestDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotificationWithoutPush(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getAccountId()).isEqualTo(CUSTOMER_ID);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.MARKETING_EVENT);
    }

    @Test
    void FCM_토큰이_없으면_스킵되고_SKIPPED_NO_TOKEN_결과가_기록되지만_알림함에는_생성된다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, null, "010-1234-5678", false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING, target));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        verify(notificationService).createNotificationWithoutPush(any());
        assertThat(capturedOutcomes().get(0).status()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_TOKEN);
    }

    @Test
    void DND_활성_고객은_푸시는_스킵되지만_알림함에는_생성된다() {
        // given — 마케팅 동의는 있지만 방해 금지 시간대인 고객 (DND 우회 버그 재현/수정 검증)
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "valid-token", "010-1234-5678", true);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING, target));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        verify(notificationService).createNotificationWithoutPush(any());
        assertThat(capturedOutcomes().get(0).status()).isEqualTo(AiDeliveryStatus.SKIPPED_DND);
    }

    @Test
    void 무효_토큰이면_결과에_invalidToken_플래그가_담긴다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "expired-token", "010-1234-5678", false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING, target));
        when(pushAdapter.send(any())).thenReturn(PushResult.failure("UNREGISTERED", true));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then — 실제 계정 무효화는 recordOutcomesInTx(claimExecutor)가 처리 — 여기서는 결과 플래그만 확인
        DeliveryOutcome outcome = capturedOutcomes().get(0);
        assertThat(outcome.status()).isEqualTo(AiDeliveryStatus.FAILED);
        assertThat(outcome.invalidToken()).isTrue();
    }

    @Test
    void KAKAO_ALERT_채널은_알림톡_Adapter로_발송되고_알림함에도_생성된다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, null, "010-1234-5678", false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.KAKAO_ALERT, AiMessageType.EVENT_MARKETING, target));
        when(alimtalkAdapter.send(any())).thenReturn(AlimtalkResult.ok("alimtalk-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(alimtalkAdapter).send(any());
        verify(pushAdapter, never()).send(any());
        assertThat(capturedOutcomes().get(0).status()).isEqualTo(AiDeliveryStatus.SENT);
        verify(notificationService).createNotificationWithoutPush(any());
    }

    @Test
    void CUSTOMER_CARE_대상은_알림함에_MARKETING_EVENT로_생성된다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "valid-token", null, false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.CUSTOMER_CARE, target));
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotificationWithoutPush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MARKETING_EVENT);
    }

    @Test
    void COMPLAINT_REPLY_타입은_알림함에_생성되지_않는다() {
        // given
        stubLockPassThrough();
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "valid-token", null, false);
        when(claimExecutor.claimInTx(MESSAGE_ID))
                .thenReturn(claimWithTarget(AiChannel.APP_PUSH, AiMessageType.COMPLAINT_REPLY, target));
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(notificationService, never()).createNotificationWithoutPush(any());
    }

    @Test
    void 알림함_content는_150자를_넘으면_말줄임으로_잘린다() {
        // given
        stubLockPassThrough();
        String longContent = "가".repeat(200);
        AccountSendInfo target = new AccountSendInfo(CUSTOMER_ID, "valid-token", "010-1234-5678", false);
        DispatchClaim claim = new DispatchClaim(true, MESSAGE_ID, STORE_ID, "테스트 상점",
                AiMessageType.EVENT_MARKETING, AiChannel.APP_PUSH, "제목", longContent, List.of(target));
        when(claimExecutor.claimInTx(MESSAGE_ID)).thenReturn(claim);
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotificationWithoutPush(captor.capture());
        assertThat(captor.getValue().getContent()).hasSize(153); // 150자 + "..."
        assertThat(captor.getValue().getContent()).endsWith("...");
    }
}
