package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiReviewInquiryResponseDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewReply;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewInquiryServiceTest {

    @InjectMocks
    private AiReviewInquiryService aiReviewInquiryService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiTextGenerator aiTextGenerator;
    @Mock private AiGeneratedMessageRepository aiGeneratedMessageRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private StoreReviewReplyRepository storeReviewReplyRepository;
    @Mock private InquiryRepository inquiryRepository;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 가게");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @Test
    void 답글이_없는_리뷰가_미답변_리뷰로_집계된다() {
        // given
        stubStore();
        StoreReview review1 = mock(StoreReview.class);
        StoreReview review2 = mock(StoreReview.class);
        when(review1.getStorereviewId()).thenReturn(1L);
        when(review2.getStorereviewId()).thenReturn(2L);
        when(storeReviewRepository.findByStore_StoreId(STORE_ID)).thenReturn(List.of(review1, review2));
        when(storeReviewReplyRepository.findByStoreReview_Store_StoreId(STORE_ID)).thenReturn(List.of());
        when(inquiryRepository.findByStore_StoreIdAndStatusOrderByCreatedAtDesc(anyLong(), eq(InquiryStatus.PENDING)))
                .thenReturn(List.of());

        // when
        AiReviewInquiryResponseDto result = aiReviewInquiryService.getOverview(OWNER_ID);

        // then
        assertThat(result.getUnansweredReviewCount()).isEqualTo(2);
        assertThat(result.isHasData()).isTrue();
    }

    @Test
    void 답글이_달린_리뷰는_미답변에서_제외된다() {
        // given
        stubStore();
        StoreReview review = mock(StoreReview.class);
        when(review.getStorereviewId()).thenReturn(1L);
        when(storeReviewRepository.findByStore_StoreId(STORE_ID)).thenReturn(List.of(review));

        StoreReviewReply reply = mock(StoreReviewReply.class);
        when(reply.getStoreReview()).thenReturn(review);
        when(storeReviewReplyRepository.findByStoreReview_Store_StoreId(STORE_ID)).thenReturn(List.of(reply));
        when(inquiryRepository.findByStore_StoreIdAndStatusOrderByCreatedAtDesc(anyLong(), eq(InquiryStatus.PENDING)))
                .thenReturn(List.of());

        // when
        AiReviewInquiryResponseDto result = aiReviewInquiryService.getOverview(OWNER_ID);

        // then
        assertThat(result.getUnansweredReviewCount()).isZero();
        assertThat(result.isHasData()).isFalse();
    }

    @Test
    void 본인_상점이_아닌_문의의_답변_초안_생성_시_INQUIRY_NOT_FOUND_예외가_발생한다() {
        // given
        Store store = stubStore();
        // findByInquiryIdAndStore_StoreId 가 empty를 반환하면 INQUIRY_NOT_FOUND 예외 발생
        when(inquiryRepository.findByInquiryIdAndStore_StoreId(eq(10L), eq(STORE_ID)))
                .thenReturn(java.util.Optional.empty());

        // when & then
        assertThatThrownBy(() -> aiReviewInquiryService.createInquiryReplyDraft(OWNER_ID, 10L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 리뷰_답글_초안이_생성되고_저장된다() {
        // given
        Store store = stubStore();
        StoreReview review = mock(StoreReview.class);
        when(review.getRating()).thenReturn(2);
        when(review.getContent()).thenReturn("음식이 맛없어요");
        when(storeReviewRepository.findByStorereviewIdAndStore_StoreId(1L, STORE_ID))
                .thenReturn(java.util.Optional.of(review));
        when(aiTextGenerator.reviewReply(any(), anyInt(), any())).thenReturn(new AiText("제목", "답글 내용"));
        AiGeneratedMessage savedMessage = AiGeneratedMessage.createDraft(
                store, store.getAccount(), com.eeum.eeum.domain.ai.enums.AiMessageType.REVIEW_REPLY,
                "STORE_REVIEW", 1L, "제목", "답글 내용",
                com.eeum.eeum.domain.ai.enums.AiChannel.APP_PUSH);
        when(aiGeneratedMessageRepository.save(any())).thenReturn(savedMessage);

        // when
        AiGeneratedMessageResponseDto result = aiReviewInquiryService.createReviewReplyDraft(OWNER_ID, 1L, false);

        // then
        assertThat(result.getContent()).isEqualTo("답글 내용");
    }
}
