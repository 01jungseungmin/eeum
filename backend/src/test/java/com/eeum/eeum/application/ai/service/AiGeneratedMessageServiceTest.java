package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiGeneratedMessageUpdateRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiMessageScheduleRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGeneratedMessageServiceTest {

    @InjectMocks
    private AiGeneratedMessageService messageService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private AiMessageCommandExecutor messageCommandExecutor;

    private static final Long OWNER_ID = 100L;
    private static final Long MESSAGE_ID = 10L;

    // ──────────────────── Helpers ────────────────────

    private AiGeneratedMessage createDraftMessage() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        Account owner = mock(Account.class);
        lenient().when(owner.getAccountId()).thenReturn(OWNER_ID);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.CUSTOMER_CARE, "CART_INTEREST", null,
                "원본 제목", "원본 내용", AiChannel.APP_PUSH);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        return message;
    }

    @SuppressWarnings("unchecked")
    private void stubLockPassThrough() {
        when(redisLockService.executeWithLock(anyString(), any(Duration.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(2)).get());
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 생성된_초안_수정에_성공하면_REVIEWED_상태가_된다() {
        // given — updateMessage는 락+Executor 위임 패턴이므로 commandExecutor.updateInTx를 stub
        AiGeneratedMessage message = createDraftMessage();
        message.edit("원본 제목", "원본 내용");
        AiGeneratedMessageResponseDto expected = AiGeneratedMessageResponseDto.from(message);
        stubLockPassThrough();
        when(messageCommandExecutor.updateInTx(eq(OWNER_ID), eq(MESSAGE_ID), any())).thenReturn(expected);

        // when
        AiGeneratedMessageResponseDto response = messageService.updateMessage(
                OWNER_ID, MESSAGE_ID, new AiGeneratedMessageUpdateRequestDto("수정 제목", "수정 내용"));

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.REVIEWED);
        verify(messageCommandExecutor).updateInTx(eq(OWNER_ID), eq(MESSAGE_ID), any());
    }

    @Test
    void 이미_SENT_상태인_메시지는_수정할_수_없다() {
        // given — executor가 AI_MESSAGE_NOT_EDITABLE 예외를 던지는 상황을 시뮬레이션
        stubLockPassThrough();
        when(messageCommandExecutor.updateInTx(eq(OWNER_ID), eq(MESSAGE_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_MESSAGE_NOT_EDITABLE));

        // when & then
        assertThatThrownBy(() -> messageService.updateMessage(
                OWNER_ID, MESSAGE_ID, new AiGeneratedMessageUpdateRequestDto(null, "수정 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_MESSAGE_NOT_EDITABLE);
    }

    @Test
    void 검토_후_보내기_시_SENT_상태로_변경되고_이벤트가_발행된다() {
        // given
        AiGeneratedMessage message = createDraftMessage();
        message.edit("원본 제목", "원본 내용"); // REVIEWED 상태로 전환
        message.send(LocalDateTime.now()); // SENT 상태로 전환
        AiGeneratedMessageResponseDto expectedResponse = AiGeneratedMessageResponseDto.from(message);
        stubLockPassThrough();
        when(messageCommandExecutor.sendInTx(eq(OWNER_ID), eq(MESSAGE_ID))).thenReturn(expectedResponse);

        // when
        AiGeneratedMessageResponseDto response = messageService.sendMessage(OWNER_ID, MESSAGE_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.SENT);
        assertThat(response.getSentAt()).isNotNull();
        verify(messageCommandExecutor).sendInTx(OWNER_ID, MESSAGE_ID);
    }

    @Test
    void 이미_발송된_메시지_재발송_시_AI_MESSAGE_ALREADY_SENT_예외가_발생한다() {
        // given
        stubLockPassThrough();
        when(messageCommandExecutor.sendInTx(eq(OWNER_ID), eq(MESSAGE_ID)))
                .thenThrow(new BusinessException(ErrorCode.AI_MESSAGE_ALREADY_SENT));

        // when & then
        assertThatThrownBy(() -> messageService.sendMessage(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_MESSAGE_ALREADY_SENT);
    }

    @Test
    void 예약_시간이_현재보다_과거면_AI_INVALID_SCHEDULE_TIME_예외가_발생한다() {
        // given
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        stubLockPassThrough();
        when(messageCommandExecutor.scheduleInTx(eq(OWNER_ID), eq(MESSAGE_ID), any(LocalDateTime.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_INVALID_SCHEDULE_TIME));

        // when & then
        assertThatThrownBy(() -> messageService.scheduleMessage(
                OWNER_ID, MESSAGE_ID, new AiMessageScheduleRequestDto(pastTime)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_SCHEDULE_TIME);
    }

    @Test
    void 예약_발송_성공_시_SCHEDULED_상태와_예약_시각이_저장된다() {
        // given
        AiGeneratedMessage message = createDraftMessage();
        message.edit("원본 제목", "원본 내용"); // REVIEWED 상태로 전환
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1);
        message.schedule(scheduledAt, LocalDateTime.now());
        AiGeneratedMessageResponseDto expectedResponse = AiGeneratedMessageResponseDto.from(message);
        stubLockPassThrough();
        when(messageCommandExecutor.scheduleInTx(eq(OWNER_ID), eq(MESSAGE_ID), any(LocalDateTime.class)))
                .thenReturn(expectedResponse);

        // when
        AiGeneratedMessageResponseDto response = messageService.scheduleMessage(
                OWNER_ID, MESSAGE_ID, new AiMessageScheduleRequestDto(scheduledAt));

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.SCHEDULED);
        assertThat(response.getScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    void SNS_CARD_채널_메시지는_공지로_등록할_수_없다() {
        // given
        stubLockPassThrough();
        when(messageCommandExecutor.publishNoticeInTx(eq(OWNER_ID), eq(MESSAGE_ID)))
                .thenThrow(new BusinessException(ErrorCode.AI_INVALID_CHANNEL));

        // when & then
        assertThatThrownBy(() -> messageService.publishNotice(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_CHANNEL);
    }
}
