package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiChatMessageRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiChatQuickQuestionDto;
import com.eeum.eeum.application.ai.dto.response.AiChatResponseDto;
import com.eeum.eeum.application.ai.dto.response.ChatActionDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChatActionType;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatbotService {

    // 범위 밖 키워드 — 코드 상수로 관리 (확장 시 이 목록만 수정)
    private static final List<String> OUT_OF_SCOPE_KEYWORDS = List.of(
            "세금", "부가세", "종소세", "주휴수당", "4대보험", "노무", "근로계약",
            "법률", "소송", "임대료", "권리금", "배달의민족", "배민", "쿠팡이츠", "외부 플랫폼");

    private static final String OUT_OF_SCOPE_MESSAGE =
            "해당 내용은 AI 매니저의 지원 범위 밖이에요. AI 매니저는 이음에 등록된 가게 운영 데이터를 기반으로 "
                    + "리뷰·문의·이벤트·고객 메시지·공지 문구·에너지·안전 체크만 도와드릴 수 있어요. "
                    + "세무·노무·법률·외부 플랫폼 비교는 해당 전문가나 기관에 문의해 주세요.";

    // 고정 추천 질문 8종 — id는 프론트 요청의 quickQuestionId와 매핑
    private static final List<AiChatQuickQuestionDto> QUICK_QUESTIONS = List.of(
            question(1, "이번 주 이벤트 뭐 할까요?", false),
            question(2, "오늘 공지 문구 써줘", true),
            question(3, "우리 가게 리뷰 요약해줘", false),
            question(4, "단골 고객 메시지 써줘", true),
            question(5, "미답변 문의 답변 초안 만들어줘", true),
            question(6, "이번 이벤트 성과 요약해줘", false),
            question(7, "에너지·안전 점검 항목 알려줘", false),
            question(8, "답글 초안 써줘", true));

    private final AiManagerSupportService supportService;
    private final AiTextGenerator aiTextGenerator;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final InquiryRepository inquiryRepository;

    public List<AiChatQuickQuestionDto> getQuickQuestions() {
        return QUICK_QUESTIONS;
    }

    @Transactional
    public AiChatResponseDto answer(Long ownerId, AiChatMessageRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.CHATBOT);

        String input = resolveInput(request);
        aiChatMessageRepository.save(AiChatMessage.create(store, store.getAccount(), AiChatRole.USER, input));

        AiChatResponseDto response = isOutOfScope(input)
                ? AiChatResponseDto.builder()
                        .text(OUT_OF_SCOPE_MESSAGE)
                        .actions(List.of())
                        .outOfScope(true)
                        .usageCounted(false)
                        .build()
                : answerInScope(store, ownerId, input);

        aiChatMessageRepository.save(
                AiChatMessage.create(store, store.getAccount(), AiChatRole.ASSISTANT, response.getText()));
        return response;
    }

    // ===================== 내부 로직 =====================

    private String resolveInput(AiChatMessageRequestDto request) {
        if (request.getQuickQuestionId() != null) {
            return QUICK_QUESTIONS.stream()
                    .filter(quick -> quick.getId() == request.getQuickQuestionId())
                    .findFirst()
                    .map(AiChatQuickQuestionDto::getQuestion)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER));
        }
        if (request.getText() == null || request.getText().isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
        return request.getText();
    }

    private boolean isOutOfScope(String input) {
        return OUT_OF_SCOPE_KEYWORDS.stream().anyMatch(input::contains);
    }

    private AiChatResponseDto answerInScope(Store store, Long ownerId, String input) {
        // 생성성 답변 — LLM 호출 성공 후 쿼터 차감 (LLM 장애 시 쿼터 소진 방지)
        if (input.contains("공지")) {
            AiText text = aiTextGenerator.noticeCopy(store.getName(), AiNoticeType.EVENT, AiTone.FRIENDLY, null);
            supportService.consumeGeneration(store, ownerId, AiFeature.CHATBOT_GENERATION, AiUsageType.CHATBOT_GENERATION);
            return generated(text.content(), AiChatActionType.OPEN_NOTICE_REGISTER, "공지 등록으로 이동");
        }
        if (input.contains("단골") || input.contains("고객 메시지")) {
            AiText text = aiTextGenerator.customerCareMessage(AiCareType.INACTIVE_REGULAR, store.getName(), null);
            supportService.consumeGeneration(store, ownerId, AiFeature.CHATBOT_GENERATION, AiUsageType.CHATBOT_GENERATION);
            return generated(text.content(), AiChatActionType.SEND_MESSAGE, "이 메시지 발송하기");
        }
        if (input.contains("문의")) {
            AiText text = aiTextGenerator.inquiryReply(store.getName(), "미답변 문의");
            supportService.consumeGeneration(store, ownerId, AiFeature.CHATBOT_GENERATION, AiUsageType.CHATBOT_GENERATION);
            return generated(text.content(), AiChatActionType.OPEN_REVIEW_DRAFT, "문의 답변 초안으로 이동");
        }
        if (input.contains("답글")) {
            AiText text = aiTextGenerator.reviewReply(store.getName(), 5, null);
            supportService.consumeGeneration(store, ownerId, AiFeature.CHATBOT_GENERATION, AiUsageType.CHATBOT_GENERATION);
            return generated(text.content(), AiChatActionType.OPEN_REVIEW_DRAFT, "리뷰 답글 초안으로 이동");
        }

        // 조회성 답변 — 사용량 미카운트
        if (input.contains("리뷰")) {
            return informational(summarizeReviews(store), AiChatActionType.OPEN_REVIEW_DRAFT, "리뷰 관리로 이동");
        }
        if (input.contains("이벤트")) {
            return informational(
                    "최근 주문 반응이 좋은 상품으로 점심 시간대(11:00~14:00) 이벤트를 추천드려요. 이벤트 성과 화면에서 추천 프리필을 확인할 수 있어요.",
                    AiChatActionType.OPEN_EVENT_REGISTER, "이벤트 등록으로 이동");
        }
        if (input.contains("에너지") || input.contains("안전") || input.contains("점검")) {
            return informational(
                    "냉방·공조 필터 청소, 냉장고 온도 점검, 조리 설비 주변 정리, 전기 배선 과부하 확인, 소화기 점검을 추천드려요.",
                    AiChatActionType.OPEN_SAFETY_CHECK, "안전 점검으로 이동");
        }

        // 범위 안 기본 응답
        return informational(
                "리뷰·문의·이벤트·고객 메시지·공지 문구·에너지·안전 체크를 도와드릴 수 있어요. 아래 추천 질문을 눌러보세요.",
                AiChatActionType.REGENERATE, "다시 물어보기");
    }

    private String summarizeReviews(Store store) {
        List<StoreReview> recentReviews = storeReviewRepository
                .findByStore_StoreIdAndCreatedAtAfter(store.getStoreId(), LocalDateTime.now().minusWeeks(2));
        if (recentReviews.isEmpty()) {
            return "최근 2주간 등록된 리뷰가 없습니다.";
        }
        double avgRating = recentReviews.stream().mapToInt(StoreReview::getRating).average().orElse(0);
        long pendingInquiries = inquiryRepository.countByStore_StoreIdAndStatus(
                store.getStoreId(), InquiryStatus.PENDING);
        return String.format("최근 2주 리뷰 %d건, 평균 평점 %.1f점입니다. 미답변 문의는 %d건 있어요.",
                recentReviews.size(), avgRating, pendingInquiries);
    }

    private AiChatResponseDto generated(String text, AiChatActionType actionType, String label) {
        return AiChatResponseDto.builder()
                .text(text)
                .actions(List.of(
                        ChatActionDto.of(label, actionType),
                        ChatActionDto.of("다시 생성", AiChatActionType.REGENERATE)))
                .outOfScope(false)
                .usageCounted(true)
                .build();
    }

    private AiChatResponseDto informational(String text, AiChatActionType actionType, String label) {
        return AiChatResponseDto.builder()
                .text(text)
                .actions(List.of(ChatActionDto.of(label, actionType)))
                .outOfScope(false)
                .usageCounted(false)
                .build();
    }

    private static AiChatQuickQuestionDto question(int id, String text, boolean generative) {
        return AiChatQuickQuestionDto.builder().id(id).question(text).generative(generative).build();
    }
}
