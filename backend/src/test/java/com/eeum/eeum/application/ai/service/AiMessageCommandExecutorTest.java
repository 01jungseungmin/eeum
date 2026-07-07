package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryAnswerRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMessageCommandExecutorTest {

    @InjectMocks
    private AiMessageCommandExecutor commandExecutor;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private StoreNoticeRepository storeNoticeRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private StoreReviewReplyRepository storeReviewReplyRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long OWNER_ID = 100L;
    private static final Long MESSAGE_ID = 10L;
    private static final Long STORE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 가게");
        return store;
    }

    private Account stubOwnerAccount() {
        Account owner = mock(Account.class);
        lenient().when(owner.getAccountId()).thenReturn(OWNER_ID);
        return owner;
    }

    private AiGeneratedMessage createReviewedMessage(
            Store store, Account owner, AiMessageType type, String targetType, Long targetId, String content) {
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, type, targetType, targetId, "제목", content, AiChannel.APP_PUSH);
        message.edit(null, null); // DRAFT -> REVIEWED
        ReflectionTestUtils.setField(message, "aiGeneratedMessageId", MESSAGE_ID);
        return message;
    }

    // ──────────────────── INQUIRY_REPLY ────────────────────

    @Test
    void INQUIRY_REPLY_메시지_발송_시_문의_답변이_등록되고_ANSWERED로_전이된다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long inquiryId = 5L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId, "답변 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        Account writer = mock(Account.class);
        lenient().when(writer.getAccountId()).thenReturn(999L);
        Inquiry inquiry = Inquiry.create(
                writer, store, InquiryTargetType.STORE, InquiryCategory.STORE, "문의 제목", "문의 내용", false);
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, STORE_ID))
                .thenReturn(Optional.of(inquiry));

        // when
        AiGeneratedMessageResponseDto response = commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.SENT);
        assertThat(response.getSentAt()).isNotNull();
        assertThat(inquiry.getStatus().name()).isEqualTo("ANSWERED");
        verify(inquiryAnswerRepository).save(any());
    }

    @Test
    void 다른_사장의_문의_targetId이면_INQUIRY_NOT_FOUND_예외가_발생한다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long inquiryId = 5L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId, "답변 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, STORE_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        verify(inquiryAnswerRepository, never()).save(any());
    }

    @Test
    void 이미_답변된_문의이면_INQUIRY_ALREADY_ANSWERED_예외가_발생하고_중복_답변이_생성되지_않는다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long inquiryId = 5L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId, "답변 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        Account writer = mock(Account.class);
        Inquiry inquiry = Inquiry.create(
                writer, store, InquiryTargetType.STORE, InquiryCategory.STORE, "문의 제목", "문의 내용", false);
        inquiry.markAnswered();
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, STORE_ID))
                .thenReturn(Optional.of(inquiry));

        // when & then
        assertThatThrownBy(() -> commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        verify(inquiryAnswerRepository, never()).save(any());
    }

    @Test
    void INQUIRY_REPLY_메시지의_targetId가_null이면_AI_INVALID_TARGET_예외가_발생한다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.INQUIRY_REPLY, "INQUIRY", null, "답변 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        // when & then
        assertThatThrownBy(() -> commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_INVALID_TARGET);
    }

    // ──────────────────── REVIEW_REPLY ────────────────────

    @Test
    void REVIEW_REPLY_메시지_발송_시_리뷰_답글이_생성된다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long reviewId = 7L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId, "답글 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        Account reviewer = mock(Account.class);
        lenient().when(reviewer.getAccountId()).thenReturn(888L);
        Order order = mock(Order.class);
        StoreReview review = StoreReview.createForOrder(store, reviewer, order, 5, "좋아요");
        ReflectionTestUtils.setField(review, "storereviewId", reviewId);
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(reviewId, STORE_ID))
                .thenReturn(Optional.of(review));
        when(storeReviewReplyRepository.existsByStoreReview_StorereviewId(reviewId)).thenReturn(false);

        // when
        AiGeneratedMessageResponseDto response = commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.SENT);
        verify(storeReviewReplyRepository).saveAndFlush(any());
    }

    @Test
    void 이미_답글_있는_리뷰이면_STORE_REVIEW_REPLY_ALREADY_EXISTS_예외가_발생한다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long reviewId = 7L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId, "답글 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        Account reviewer = mock(Account.class);
        Order order = mock(Order.class);
        StoreReview review = StoreReview.createForOrder(store, reviewer, order, 5, "좋아요");
        ReflectionTestUtils.setField(review, "storereviewId", reviewId);
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(reviewId, STORE_ID))
                .thenReturn(Optional.of(review));
        when(storeReviewReplyRepository.existsByStoreReview_StorereviewId(reviewId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_REVIEW_REPLY_ALREADY_EXISTS);
        verify(storeReviewReplyRepository, never()).saveAndFlush(any());
    }

    @Test
    void 다른_사장의_리뷰_targetId이면_STORE_REVIEW_NOT_FOUND_예외가_발생한다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        Long reviewId = 7L;
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId, "답글 내용");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(reviewId, STORE_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_REVIEW_NOT_FOUND);
    }

    // ──────────────────── 일반 메시지 (도메인 연동 없음) ────────────────────

    @Test
    void CUSTOMER_CARE_메시지는_도메인_연동_없이_SENT_처리만_된다() {
        // given
        Store store = stubStore();
        Account owner = stubOwnerAccount();
        AiGeneratedMessage message = createReviewedMessage(
                store, owner, AiMessageType.CUSTOMER_CARE, "CART_INTEREST", null, "케어 메시지");
        when(supportService.getOwnedMessage(OWNER_ID, MESSAGE_ID)).thenReturn(message);

        // when
        AiGeneratedMessageResponseDto response = commandExecutor.sendInTx(OWNER_ID, MESSAGE_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(AiMessageStatus.SENT);
        verify(inquiryAnswerRepository, never()).save(any());
        verify(storeReviewReplyRepository, never()).saveAndFlush(any());
    }
}
