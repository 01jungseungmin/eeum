package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMessageDispatchServiceTest {

    @InjectMocks
    private AiMessageDispatchService dispatchService;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiMessageDeliveryRepository aiMessageDeliveryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private NotificationSettingsRepository notificationSettingsRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Mock private PushAdapter pushAdapter;
    @Mock private AlimtalkAdapter alimtalkAdapter;
    @Mock private RedisLockService redisLockService;
    @Spy private AlimtalkProperties alimtalkProperties = new AlimtalkProperties(
            "mock", null, null, null, null,
            new AlimtalkProperties.Templates("CARE_01", "MKT_01", "NOTICE_01"));

    private static final Long MESSAGE_ID = 1L;
    private static final Long OWNER_ID = 100L;
    private static final Long CUSTOMER_ID = 10L;

    // ──────────────────── Helpers ────────────────────

    private void stubLockPassThrough() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));
    }

    private AiGeneratedMessage createMessage(AiChannel channel, AiMessageType type) {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        Account owner = mock(Account.class);
        lenient().when(owner.getAccountId()).thenReturn(OWNER_ID);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, type, null, null, "제목", "본문", channel);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        return message;
    }

    private Account stubCustomer(String fcmToken) {
        Account customer = mock(Account.class);
        lenient().when(customer.getAccountId()).thenReturn(CUSTOMER_ID);
        lenient().when(customer.getFcmToken()).thenReturn(fcmToken);
        lenient().when(customer.getPhone()).thenReturn("010-1234-5678");
        when(accountRepository.findAllById(any())).thenReturn(List.of(customer));
        return customer;
    }

    private void stubMarketingTargets(AiGeneratedMessage message, boolean consented) {
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong())).thenReturn(List.of(CUSTOMER_ID));
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(notificationSettingsRepository.findMarketingEnabledAccountIds(anyList()))
                .thenReturn(consented ? List.of(CUSTOMER_ID) : List.of());
    }

    private AiMessageDelivery capturedDelivery() {
        ArgumentCaptor<AiMessageDelivery> captor = ArgumentCaptor.forClass(AiMessageDelivery.class);
        verify(aiMessageDeliveryRepository).save(captor.capture());
        return captor.getValue();
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void APP_PUSH_메시지_발송_시_FCM이_호출되고_SENT_기록이_저장된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, true);
        stubCustomer("valid-token");
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter).send(any());
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
        assertThat(delivery.getSentAt()).isNotNull();
    }

    @Test
    void FCM_토큰이_없는_고객은_스킵되고_SKIPPED_NO_TOKEN으로_기록된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, true);
        stubCustomer(null);

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_TOKEN);
    }

    @Test
    void 무효_토큰이면_기존_정책대로_계정의_FCM_토큰을_무효화한다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, true);
        Account customer = stubCustomer("expired-token");
        when(pushAdapter.send(any())).thenReturn(PushResult.failure("UNREGISTERED", true));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(customer).updateFcmToken(null);
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.FAILED);
    }

    @Test
    void 마케팅_수신_동의가_없는_고객은_발송에서_제외된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, false);
        stubCustomer("valid-token");

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_CONSENT);
    }

    @Test
    void 이미_발송_기록이_있는_메시지는_재발송하지_않는다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(true);

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        verify(aiMessageDeliveryRepository, never()).save(any());
    }

    @Test
    void 발송_대상이_없으면_NO_TARGET으로_기록된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong())).thenReturn(List.of());
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any())).thenReturn(List.of());

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getTargetAccountId()).isNull();
    }

    @Test
    void KAKAO_ALERT_채널_메시지는_알림톡_Adapter로_발송된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.KAKAO_ALERT, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, true);
        stubCustomer("any");
        when(alimtalkAdapter.send(any())).thenReturn(AlimtalkResult.ok("alimtalk-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(alimtalkAdapter).send(any());
        verify(pushAdapter, never()).send(any());
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.SENT);
    }

    @Test
    void STORE_NOTICE_채널은_고객_발송_없이_종료된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.STORE_NOTICE, AiMessageType.NOTICE);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        verify(pushAdapter, never()).send(any());
        verify(alimtalkAdapter, never()).send(any());
    }
}
