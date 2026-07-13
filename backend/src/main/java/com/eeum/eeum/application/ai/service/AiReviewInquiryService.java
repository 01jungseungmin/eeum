package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiComplaintDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiReviewInquiryResponseDto;
import com.eeum.eeum.application.ai.dto.response.UnansweredInquiryDto;
import com.eeum.eeum.application.ai.dto.response.UnansweredReviewDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiReviewInquiryService {

    // 반복 불만 키워드 후보 — 최근 2주 저평점 리뷰 본문에서 매칭
    private static final List<String> COMPLAINT_KEYWORD_CANDIDATES = List.of(
            "포장", "배달", "위생", "대기", "친절", "가격", "양", "맛", "지연", "누락");

    private final AiManagerSupportService supportService;
    private final AiTextGenerator aiTextGenerator;
    private final AiDraftPersistenceExecutor draftPersistenceExecutor;
    private final StoreReviewRepository storeReviewRepository;
    private final StoreReviewReplyRepository storeReviewReplyRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public AiReviewInquiryResponseDto getOverview(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.REVIEW_INQUIRY_VIEW);
        Long storeId = store.getStoreId();

        List<StoreReview> unansweredReviews = findUnansweredReviews(storeId);
        List<Inquiry> unansweredInquiries =
                inquiryRepository.findByStore_StoreIdAndStatusOrderByCreatedAtDesc(storeId, InquiryStatus.PENDING);
        List<String> complaintKeywords = extractComplaintKeywords(storeId);

        boolean hasData = !unansweredReviews.isEmpty() || !unansweredInquiries.isEmpty() || !complaintKeywords.isEmpty();

        return AiReviewInquiryResponseDto.builder()
                .complaintKeywords(complaintKeywords)
                .unansweredReviewCount(unansweredReviews.size())
                .unansweredInquiryCount(unansweredInquiries.size())
                .unansweredReviews(unansweredReviews.stream()
                        .map(review -> UnansweredReviewDto.builder()
                                .reviewId(review.getStorereviewId())
                                .rating(review.getRating())
                                .content(review.getContent())
                                .createdAt(review.getCreatedAt())
                                .build())
                        .toList())
                .unansweredInquiries(unansweredInquiries.stream()
                        .map(inquiry -> UnansweredInquiryDto.builder()
                                .inquiryId(inquiry.getInquiryId())
                                .title(inquiry.getTitle())
                                .createdAt(inquiry.getCreatedAt())
                                .build())
                        .toList())
                .recommendedResponse(hasData
                        ? "미답변 리뷰와 문의부터 처리하는 것을 추천드려요. 초안 생성으로 빠르게 답변해보세요."
                        : null)
                .hasData(hasData)
                .emptyMessage(hasData ? null : "처리할 리뷰나 문의가 없습니다.")
                .build();
    }

    // @Transactional을 두지 않는다 — LLM 호출(외부 HTTP)이 DB 트랜잭션을 오래 붙잡지 않도록,
    // 생성은 트랜잭션 밖에서 하고 저장만 saveDraft(draftPersistenceExecutor)의 짧은 트랜잭션에 위임한다.
    public AiGeneratedMessageResponseDto createReviewReplyDraft(Long ownerId, Long reviewId, boolean confirmDelete) {
        Store store = supportService.getOwnerStore(ownerId);
        StoreReview review = storeReviewRepository
                .findByStorereviewIdAndStore_StoreId(reviewId, store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_REVIEW_NOT_FOUND));

        supportService.enforceDraftCapacity(store, AiMessageType.REVIEW_REPLY, confirmDelete);
        supportService.consumeGeneration(store, ownerId, AiFeature.REVIEW_REPLY_DRAFT, AiUsageType.REVIEW_REPLY_DRAFT);

        AiText text = aiTextGenerator.reviewReply(store.getName(), review.getRating(), review.getContent());
        return saveDraft(store, AiMessageType.REVIEW_REPLY, "STORE_REVIEW", reviewId, text, "리뷰 답글 초안 생성");
    }

    public AiGeneratedMessageResponseDto createInquiryReplyDraft(Long ownerId, Long inquiryId, boolean confirmDelete) {
        Store store = supportService.getOwnerStore(ownerId);
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndStore_StoreId(inquiryId, store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));

        supportService.enforceDraftCapacity(store, AiMessageType.INQUIRY_REPLY, confirmDelete);
        supportService.consumeGeneration(store, ownerId, AiFeature.INQUIRY_REPLY_DRAFT, AiUsageType.INQUIRY_REPLY_DRAFT);

        AiText text = aiTextGenerator.inquiryReply(store.getName(), inquiry.getTitle());
        return saveDraft(store, AiMessageType.INQUIRY_REPLY, "INQUIRY", inquiryId, text, "문의 답변 초안 생성");
    }

    public AiGeneratedMessageResponseDto createComplaintDraft(Long ownerId, AiComplaintDraftRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.enforceDraftCapacity(store, AiMessageType.COMPLAINT_REPLY, request.isConfirmDelete());
        supportService.consumeGeneration(store, ownerId, AiFeature.COMPLAINT_DRAFT, AiUsageType.COMPLAINT_DRAFT);

        AiText text = aiTextGenerator.complaintReply(store.getName(), request.getKeyword());
        return saveDraft(store, AiMessageType.COMPLAINT_REPLY, "COMPLAINT_KEYWORD", null, text, "반복 불만 대응 문구 생성");
    }

    // ===================== 내부 유틸 =====================

    private List<StoreReview> findUnansweredReviews(Long storeId) {
        Set<Long> repliedReviewIds = storeReviewReplyRepository.findByStoreReview_Store_StoreId(storeId).stream()
                .map(reply -> reply.getStoreReview().getStorereviewId())
                .collect(Collectors.toSet());
        return storeReviewRepository.findByStore_StoreId(storeId).stream()
                .filter(review -> !repliedReviewIds.contains(review.getStorereviewId()))
                .toList();
    }

    private List<String> extractComplaintKeywords(Long storeId) {
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);
        List<StoreReview> recentLowReviews = storeReviewRepository
                .findByStore_StoreIdAndCreatedAtAfter(storeId, twoWeeksAgo).stream()
                .filter(review -> review.getRating() <= 3)
                .toList();

        return COMPLAINT_KEYWORD_CANDIDATES.stream()
                .filter(keyword -> recentLowReviews.stream()
                        .filter(review -> review.getContent() != null && review.getContent().contains(keyword))
                        .count() >= 2) // 2회 이상 등장해야 "반복" 불만으로 판단
                .toList();
    }

    private AiGeneratedMessageResponseDto saveDraft(
            Store store, AiMessageType type, String targetType, Long targetId, AiText text, String description) {
        AiGeneratedMessage message = draftPersistenceExecutor.saveDraftInTx(
                store, store.getAccount(), type, targetType, targetId,
                text.title(), text.content(), AiChannel.APP_PUSH, targetType, description);
        supportService.healDraftCapacity(store, type);
        return AiGeneratedMessageResponseDto.from(message);
    }
}
