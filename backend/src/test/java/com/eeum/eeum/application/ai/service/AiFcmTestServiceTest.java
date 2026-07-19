package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiFcmTestSendRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiFcmTestSendResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushMessage;
import com.eeum.eeum.infrastructure.push.PushResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFcmTestServiceTest {

    @InjectMocks
    private AiFcmTestService fcmTestService;

    @Mock private AiManagerSupportService supportService;
    @Mock private PushAdapter pushAdapter;
    @Mock private RateLimitService rateLimitService;

    private static final Long OWNER_ID = 100L;
    private static final Long MESSAGE_ID = 10L;

    private void stubStore() {
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(mock(Store.class));
    }

    @Test
    void messageId_기반_테스트_발송_시_해당_메시지의_제목과_본문을_사용한다() {
        // given
        stubStore();
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                mock(Store.class), mock(Account.class), AiMessageType.CUSTOMER_CARE,
                null, null, "AI 제목", "AI가 생성한 본문", AiChannel.APP_PUSH);
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-message-id"));

        // when
        AiFcmTestSendResponseDto response = fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(MESSAGE_ID, "device-token", null, null));

        // then
        assertThat(response.success()).isTrue();
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushAdapter).send(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("AI 제목");
        assertThat(captor.getValue().getBody()).isEqualTo("AI가 생성한 본문");
        assertThat(captor.getValue().getFcmToken()).isEqualTo("device-token");
    }

    @Test
    void 직접_입력한_title과_content로_테스트_발송할_수_있다() {
        // given
        stubStore();
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-message-id"));

        // when
        AiFcmTestSendResponseDto response = fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(null, "device-token", "직접 제목", "직접 본문"));

        // then
        assertThat(response.success()).isTrue();
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushAdapter).send(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("직접 제목");
        assertThat(captor.getValue().getBody()).isEqualTo("직접 본문");
    }

    @Test
    void Rate_Limit_초과_시_AI_RATE_LIMITED_예외가_발생하고_발송하지_않는다() {
        // given — fcmToken @NotBlank는 컨트롤러 @Valid로 처리. 서비스는 Rate Limit을 검사한다.
        stubStore();
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.AI_RATE_LIMITED))
                .when(rateLimitService).checkCooldown(anyString(), any(), any());

        // when & then
        assertThatThrownBy(() -> fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(null, "device-token", "제목", "본문")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_RATE_LIMITED);
        verify(pushAdapter, never()).send(any());
    }

    @Test
    void 타_사장의_messageId로_발송_요청_시_AI_FORBIDDEN이_전파된다() {
        // given
        stubStore();
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID))
                .thenThrow(new BusinessException(ErrorCode.AI_FORBIDDEN));

        // when & then
        assertThatThrownBy(() -> fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(MESSAGE_ID, "device-token", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_FORBIDDEN);
        verify(pushAdapter, never()).send(any());
    }

    @Test
    void 발송할_본문이_없으면_예외가_발생한다() {
        // given
        stubStore();

        // when & then
        assertThatThrownBy(() -> fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(null, "device-token", "제목", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_INVALID_INPUT);
    }

    @Test
    void Firebase_발송_실패_시_실패_응답을_반환한다() {
        // given
        stubStore();
        when(pushAdapter.send(any())).thenReturn(PushResult.failure("UNREGISTERED", true));

        // when
        AiFcmTestSendResponseDto response = fcmTestService.sendTestPush(
                OWNER_ID, new AiFcmTestSendRequestDto(null, "expired-token", "제목", "본문"));

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("UNREGISTERED");
    }
}
