package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.AccountSendInfo;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DeliveryOutcome;
import com.eeum.eeum.application.ai.service.AiMessageDispatchClaimExecutor.DispatchClaim;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMessageDispatchClaimExecutorTest {

    @InjectMocks
    private AiMessageDispatchClaimExecutor claimExecutor;

    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiMessageDeliveryRepository aiMessageDeliveryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private NotificationSettingsRepository notificationSettingsRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Mock private StoreReviewRepository storeReviewRepository;

    private static final Long MESSAGE_ID = 1L;
    private static final Long OWNER_ID = 100L;
    private static final Long CUSTOMER_ID = 10L;

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
        ReflectionTestUtils.setField(message, "status", AiMessageStatus.SENT);
        return message;
    }

    private void stubNoExistingDelivery(AiGeneratedMessage message) {
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);
    }

    private void stubMarketingTargets(AiGeneratedMessage message, boolean consented) {
        stubNoExistingDelivery(message);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong())).thenReturn(List.of(CUSTOMER_ID));
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(notificationSettingsRepository.findMarketingEnabledAccountIds(anyList()))
                .thenReturn(consented ? List.of(CUSTOMER_ID) : List.of());
        lenient().when(notificationSettingsRepository.findByAccount_AccountIdIn(anyList())).thenReturn(List.of());
    }

    private Account stubCustomer(String fcmToken) {
        Account customer = mock(Account.class);
        lenient().when(customer.getAccountId()).thenReturn(CUSTOMER_ID);
        lenient().when(customer.getFcmToken()).thenReturn(fcmToken);
        lenient().when(customer.getPhone()).thenReturn("010-1234-5678");
        when(accountRepository.findAllById(any())).thenReturn(List.of(customer));
        return customer;
    }

    private AiMessageDelivery capturedDelivery() {
        ArgumentCaptor<AiMessageDelivery> captor = ArgumentCaptor.forClass(AiMessageDelivery.class);
        verify(aiMessageDeliveryRepository).save(captor.capture());
        return captor.getValue();
    }

    // ──────────────────── claimInTx: 멱등/상태/채널 가드 ────────────────────

    @Test
    void 이미_발송_기록이_있는_메시지는_재발송_대상에서_제외된다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(true);

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        verify(aiMessageDeliveryRepository, never()).save(any());
    }

    @Test
    void 취소된_메시지는_상태_재검증에서_걸러져_발송_대상에서_제외된다() {
        // given — 스케줄러의 isDispatchable 통과 직후 사장이 취소한 상황(M-4 재현)
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        ReflectionTestUtils.setField(message, "status", AiMessageStatus.CANCELLED);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        when(aiMessageDeliveryRepository.existsByMessage_AiGeneratedMessageId(MESSAGE_ID)).thenReturn(false);

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        verify(aiMessageDeliveryRepository, never()).save(any());
    }

    @Test
    void SCHEDULED_상태_메시지는_예약발송_경로로_정상_진행된다() {
        // given — 스케줄러 경로는 markSent 이전(dispatch 시점)엔 아직 SCHEDULED 상태다
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        ReflectionTestUtils.setField(message, "status", AiMessageStatus.SCHEDULED);
        stubMarketingTargets(message, true);
        stubCustomer("valid-token");

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isTrue();
    }

    @Test
    void STORE_NOTICE_채널은_고객_발송_대상이_아니다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.STORE_NOTICE, AiMessageType.NOTICE);
        stubNoExistingDelivery(message);

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
    }

    // ──────────────────── claimInTx: 마케팅/공지 대상 확정 ────────────────────

    @Test
    void APP_PUSH_대상이_확정되면_DispatchClaim에_담긴다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, true);
        stubCustomer("valid-token");

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isTrue();
        assertThat(claim.targets()).hasSize(1);
        AccountSendInfo target = claim.targets().get(0);
        assertThat(target.accountId()).isEqualTo(CUSTOMER_ID);
        assertThat(target.fcmToken()).isEqualTo("valid-token");
        assertThat(target.dndActive()).isFalse();
    }

    @Test
    void DND_활성_계정은_dndActive_플래그가_true로_담긴다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubNoExistingDelivery(message);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong())).thenReturn(List.of(CUSTOMER_ID));
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(notificationSettingsRepository.findMarketingEnabledAccountIds(anyList())).thenReturn(List.of(CUSTOMER_ID));
        stubCustomer("valid-token");
        NotificationSettings settings = mock(NotificationSettings.class);
        Account settingsAccount = mock(Account.class);
        when(settingsAccount.getAccountId()).thenReturn(CUSTOMER_ID);
        when(settings.getAccount()).thenReturn(settingsAccount);
        when(settings.isDndActive()).thenReturn(true);
        when(notificationSettingsRepository.findByAccount_AccountIdIn(anyList())).thenReturn(List.of(settings));

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.targets()).hasSize(1);
        assertThat(claim.targets().get(0).dndActive()).isTrue();
    }

    @Test
    void 마케팅_수신_동의가_없는_고객은_SKIPPED_NO_CONSENT로_기록되고_대상에서_제외된다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubMarketingTargets(message, false);
        stubCustomer("valid-token");

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        assertThat(capturedDelivery().getStatus()).isEqualTo(AiDeliveryStatus.SKIPPED_NO_CONSENT);
    }

    @Test
    void 발송_대상이_없으면_NO_TARGET으로_기록된다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        stubNoExistingDelivery(message);
        when(favoriteRepository.findAccountIdsByRefTypeAndRefId(any(), anyLong())).thenReturn(List.of());
        when(orderRepository.findOrderAccountIdsByStoreIdAndStatus(anyLong(), any())).thenReturn(List.of());

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getTargetAccountId()).isNull();
    }

    @Test
    void CUSTOMER_CARE_CART_INTEREST_대상은_장바구니_보유_고객이다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.CUSTOMER_CARE, "CART_INTEREST", null);
        stubNoExistingDelivery(message);
        com.eeum.eeum.domain.order.entity.Cart cart = mock(com.eeum.eeum.domain.order.entity.Cart.class);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);
        when(cart.getAccount()).thenReturn(customer);
        when(cartRepository.findByStore_StoreId(anyLong())).thenReturn(List.of(cart));
        when(notificationSettingsRepository.findMarketingEnabledAccountIds(anyList())).thenReturn(List.of(CUSTOMER_ID));
        when(notificationSettingsRepository.findByAccount_AccountIdIn(anyList())).thenReturn(List.of());
        stubCustomer("valid-token");

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isTrue();
        assertThat(claim.targets()).extracting(AccountSendInfo::accountId).containsExactly(CUSTOMER_ID);
    }

    @Test
    void COMPLAINT_REPLY는_개별_수신자가_없어_구체적인_사유와_함께_NO_TARGET으로_기록된다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.COMPLAINT_REPLY, "COMPLAINT_KEYWORD", null);
        stubNoExistingDelivery(message);

        // when
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getFailedReason()).isEqualTo("반복 불만 대응 문구는 개별 수신자 없음");
    }

    // ──────────────────── claimInTx: INQUIRY_REPLY / REVIEW_REPLY 발송 이력 ────────────────────

    @Test
    void INQUIRY_REPLY_발송_시_문의_작성자_accountId로_SENT_기록되고_대상은_비어있다() {
        // given
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
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then — 알림은 sendInTx 단계에서 이미 나갔으므로 여기서는 기록만 남기고 targets는 비어있다
        assertThat(claim.shouldSendToTargets()).isFalse();
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void INQUIRY_REPLY_대상_문의를_찾지_못하면_NO_TARGET과_구체적인_사유가_기록된다() {
        // given
        Long inquiryId = 5L;
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId);
        stubNoExistingDelivery(message);
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, message.getStore().getStoreId()))
                .thenReturn(Optional.empty());

        // when
        claimExecutor.claimInTx(MESSAGE_ID);

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.NO_TARGET);
        assertThat(delivery.getFailedReason()).isEqualTo("문의 작성자 계정 없음");
    }

    @Test
    void REVIEW_REPLY_발송_시_리뷰_작성자_accountId로_SENT_기록된다() {
        // given
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
        DispatchClaim claim = claimExecutor.claimInTx(MESSAGE_ID);

        // then
        assertThat(claim.shouldSendToTargets()).isFalse();
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
    }

    // ──────────────────── recordOutcomesInTx ────────────────────

    @Test
    void recordOutcomesInTx는_결과별로_delivery를_저장한다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        DeliveryOutcome outcome = new DeliveryOutcome(
                CUSTOMER_ID, AiChannel.APP_PUSH, AiDeliveryStatus.SENT, null, "fcm-id", false);

        // when
        claimExecutor.recordOutcomesInTx(MESSAGE_ID, List.of(outcome));

        // then
        AiMessageDelivery delivery = capturedDelivery();
        assertThat(delivery.getStatus()).isEqualTo(AiDeliveryStatus.SENT);
        assertThat(delivery.getTargetAccountId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void invalidToken_결과가_있으면_계정의_FCM_토큰을_무효화한다() {
        // given
        AiGeneratedMessage message = createMessage(AiChannel.APP_PUSH, AiMessageType.EVENT_MARKETING);
        when(aiGeneratedMessageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        Account account = mock(Account.class);
        when(accountRepository.findAllById(any())).thenReturn(List.of(account));
        DeliveryOutcome outcome = new DeliveryOutcome(
                CUSTOMER_ID, AiChannel.APP_PUSH, AiDeliveryStatus.FAILED, "UNREGISTERED", null, true);

        // when
        claimExecutor.recordOutcomesInTx(MESSAGE_ID, List.of(outcome));

        // then
        verify(account).updateFcmToken(null);
    }

    @Test
    void 결과가_비어있으면_아무_것도_하지_않는다() {
        // when
        claimExecutor.recordOutcomesInTx(MESSAGE_ID, List.of());

        // then
        verify(aiGeneratedMessageRepository, never()).findById(any());
        verify(aiMessageDeliveryRepository, never()).save(any());
    }
}
