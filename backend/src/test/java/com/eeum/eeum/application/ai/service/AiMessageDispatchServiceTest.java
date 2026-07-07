package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
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
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
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
    @Mock private NotificationService notificationService;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
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
        return createMessage(channel, type, null, null);
    }

    private AiGeneratedMessage createMessage(AiChannel channel, AiMessageType type, String targetType, Long targetId) {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        Account owner = mock(Account.class);
        lenient().when(owner.getAccountId()).thenReturn(OWNER_ID);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, type, targetType, targetId, "제목", "본문", channel);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        return message;
    }

    private void stubNoExistingDelivery(AiGeneratedMessage message) {
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);
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
    void APP_PUSH_메시지_발송_시_FCM이_호출되고_SENT_기록이_저장되고_알림함에도_생성된다() {
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

        ArgumentCaptor<NotificationCreateRequestDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotificationWithoutPush(notificationCaptor.capture());
        NotificationCreateRequestDto notification = notificationCaptor.getValue();
        assertThat(notification.getAccountId()).isEqualTo(CUSTOMER_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.MARKETING_EVENT);
    }

    @Test
    void FCM_토큰이_없는_고객은_스킵되고_SKIPPED_NO_TOKEN으로_기록되지만_알림함에는_생성된다() {
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
        verify(notificationService).createNotificationWithoutPush(any());
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
    void 마케팅_수신_동의가_없는_고객은_발송에서_제외되고_알림함에도_생성되지_않는다() {
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
        verify(notificationService, never()).createNotificationWithoutPush(any());
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
    void 발송_대상이_없으면_NO_TARGET으로_기록되고_알림함에도_생성되지_않는다() {
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
        verify(notificationService, never()).createNotificationWithoutPush(any());
    }

    @Test
    void KAKAO_ALERT_채널_메시지는_알림톡_Adapter로_발송되고_알림함에도_생성된다() {
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
        verify(notificationService).createNotificationWithoutPush(any());
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

    // ──────────────────── INQUIRY_REPLY / REVIEW_REPLY 발송 이력 ────────────────────

    @Test
    void INQUIRY_REPLY_발송_시_문의_작성자_accountId로_SENT_기록되고_재발송하지_않는다() {
        // given
        stubLockPassThrough();
        Long inquiryId = 5L;
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId);
        stubNoExistingDelivery(message);

        Store store = message.getStore();
        Account writer = mock(Account.class);
        when(writer.getAccountId()).thenReturn(CUSTOMER_ID);
        Inquiry inquiry = Inquiry.create(
                writer, store, InquiryTargetType.STORE, InquiryCategory.STORE, "문의 제목", "문의 내용", false);
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, store.getStoreId()))
                .thenReturn(Optional.of(inquiry));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
        verify(pushAdapter, never()).send(any());
        verify(alimtalkAdapter, never()).send(any());
        // 알림함 알림은 sendInTx 단계의 InquiryAnsweredEvent 경유로 이미 생성됨 — 여기서 중복 생성하지 않는다
        verify(notificationService, never()).createNotificationWithoutPush(any());
    }

    @Test
    void INQUIRY_REPLY_대상_문의를_찾지_못하면_NO_TARGET과_구체적인_사유가_기록된다() {
        // given
        stubLockPassThrough();
        Long inquiryId = 5L;
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId);
        stubNoExistingDelivery(message);
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, message.getStore().getStoreId()))
                .thenReturn(Optional.empty());

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getTargetAccountId()).isNull();
        assertThat(delivery.getFailedReason()).isEqualTo("문의 작성자 계정 없음");
    }

    @Test
    void REVIEW_REPLY_발송_시_리뷰_작성자_accountId로_SENT_기록되고_재발송하지_않는다() {
        // given
        stubLockPassThrough();
        Long reviewId = 7L;
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId);
        stubNoExistingDelivery(message);

        Store store = message.getStore();
        Account reviewer = mock(Account.class);
        when(reviewer.getAccountId()).thenReturn(CUSTOMER_ID);
        Order order = mock(Order.class);
        StoreReview review = StoreReview.createForOrder(store, reviewer, order, 5, "좋아요");
        ReflectionTestUtils.setField(review, "storereviewId", reviewId);
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(reviewId, store.getStoreId()))
                .thenReturn(Optional.of(review));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
        verify(pushAdapter, never()).send(any());
        verify(alimtalkAdapter, never()).send(any());
        // 알림함 알림은 sendInTx 단계의 StoreReviewReplyCreatedEvent 경유로 이미 생성됨 — 여기서 중복 생성하지 않는다
        verify(notificationService, never()).createNotificationWithoutPush(any());
    }

    @Test
    void REVIEW_REPLY_대상_리뷰를_찾지_못하면_NO_TARGET과_구체적인_사유가_기록된다() {
        // given
        stubLockPassThrough();
        Long reviewId = 7L;
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId);
        stubNoExistingDelivery(message);
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(reviewId, message.getStore().getStoreId()))
                .thenReturn(Optional.empty());

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getTargetAccountId()).isNull();
        assertThat(delivery.getFailedReason()).isEqualTo("리뷰 작성자 계정 없음");
    }

    @Test
    void COMPLAINT_REPLY는_개별_수신자가_없어_구체적인_사유와_함께_NO_TARGET으로_기록된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.COMPLAINT_REPLY, "COMPLAINT_KEYWORD", null);
        stubNoExistingDelivery(message);

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getFailedReason()).isEqualTo("반복 불만 대응 문구는 개별 수신자 없음");
        verify(notificationService, never()).createNotificationWithoutPush(any());
    }

    // ──────────────────── CUSTOMER_CARE 알림함 연동 ────────────────────

    @Test
    void CUSTOMER_CARE_발송_성공_시_알림함에도_MARKETING_EVENT로_생성된다() {
        // given
        stubLockPassThrough();
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.CUSTOMER_CARE, "CART_INTEREST", null);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);

        com.eeum.eeum.domain.order.entity.Cart cart = mock(com.eeum.eeum.domain.order.entity.Cart.class);
        Account customer = mock(Account.class);
        lenient().when(customer.getAccountId()).thenReturn(CUSTOMER_ID);
        lenient().when(customer.getFcmToken()).thenReturn("valid-token");
        when(cart.getAccount()).thenReturn(customer);
        when(cartRepository.findByStore_StoreId(anyLong())).thenReturn(List.of(cart));
        when(notificationSettingsRepository.findMarketingEnabledAccountIds(anyList())).thenReturn(List.of(CUSTOMER_ID));
        when(accountRepository.findAllById(any())).thenReturn(List.of(customer));
        when(pushAdapter.send(any())).thenReturn(PushResult.success("fcm-id"));

        // when
        dispatchService.dispatch(MESSAGE_ID);

        // then
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotificationWithoutPush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MARKETING_EVENT);
        assertThat(captor.getValue().getAccountId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void 알림함_content는_150자를_넘으면_말줄임으로_잘린다() {
        // given
        stubLockPassThrough();
        String longContent = "가".repeat(200);
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(1L);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        Account owner = mock(Account.class);
        lenient().when(owner.getAccountId()).thenReturn(OWNER_ID);
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.EVENT_MARKETING, null, null, "제목", longContent, AiChannel.APP_PUSH);
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        stubMarketingTargets(message, true);
        stubCustomer("valid-token");
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
