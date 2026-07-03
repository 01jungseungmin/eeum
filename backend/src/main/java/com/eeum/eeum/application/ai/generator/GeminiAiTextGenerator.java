package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.router.AiModelRouter;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * AiTextGenerator의 실제 LLM 구현체 — AiModelRouter 경유로 Gemini/Groq/Mock 호출.
 * Router까지 전부 실패하면 TemplateAiTextGenerator로 최종 fallback한다.
 * (Service는 계속 AiTextGenerator 인터페이스에만 의존 — 응답 구조 변경 없음)
 */
@Slf4j
public class GeminiAiTextGenerator implements AiTextGenerator {

    private static final String SYSTEM_PROMPT = """
            너는 지역 소상공인을 돕는 이음 서비스의 AI 매니저다.
            한국어로 답변한다.
            사장님에게 보여줄 문장은 존댓말을 사용한다.
            고객에게 보낼 문구는 과장 광고를 피하고 친근하지만 부담스럽지 않게 작성한다.
            확실하지 않은 수치는 만들지 않는다.
            제공된 데이터만 근거로 사용한다.
            개인정보를 노출하지 않는다.
            불필요한 영어 표현을 사용하지 않는다.""";

    private final AiModelRouter router;
    private final TemplateAiTextGenerator templateFallback;
    private final ObjectMapper objectMapper;

    public GeminiAiTextGenerator(
            AiModelRouter router,
            TemplateAiTextGenerator templateFallback,
            ObjectMapper objectMapper
    ) {
        this.router = router;
        this.templateFallback = templateFallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiText customerCareMessage(AiCareType careType, String storeName, String contextHint) {
        String prompt = """
                가게 '%s'의 고객에게 보낼 짧은 케어 메시지를 작성한다.
                대상: %s
                참고 정보: %s
                고객이 부담을 느끼지 않도록 짧고 정중하게 작성한다.
                본문 문장만 반환한다. 150자 이내로 작성한다.""" .formatted(
                storeName, careType.getTitle(),
                contextHint != null && !contextHint.isBlank() ? contextHint : "없음");
        try {
            String content = router.generate(AiTaskType.OWNER_REPLY_DRAFT, SYSTEM_PROMPT, prompt).content();
            return AiText.of(careType.getTitle(), content);
        } catch (AiClientException e) {
            return fallback("customerCareMessage", e,
                    () -> templateFallback.customerCareMessage(careType, storeName, contextHint));
        }
    }

    @Override
    public AiText reviewReply(String storeName, int rating, String reviewContent) {
        String prompt = """
                고객 리뷰에 대한 사장님 답글 초안을 작성한다.
                고객을 탓하지 않는다.
                낮은 평점 리뷰에는 사과, 개선 의지, 재방문 유도 순서로 작성한다.
                높은 평점 리뷰에는 감사, 구체적 언급, 재방문 유도 순서로 작성한다.
                300자 이내로 작성하고 답글 본문만 반환한다.

                가게 이름: %s
                평점: %d점 (5점 만점)
                리뷰 내용: %s""".formatted(
                storeName, rating,
                reviewContent != null && !reviewContent.isBlank() ? reviewContent : "(내용 없음)");
        try {
            return AiText.content(router.generate(AiTaskType.OWNER_REPLY_DRAFT, SYSTEM_PROMPT, prompt).content());
        } catch (AiClientException e) {
            return fallback("reviewReply", e,
                    () -> templateFallback.reviewReply(storeName, rating, reviewContent));
        }
    }

    @Override
    public AiText inquiryReply(String storeName, String inquiryTitle) {
        String prompt = """
                고객 문의에 대한 사장님 답변 초안을 작성한다.
                확정할 수 없는 내용은 단정하지 않는다.
                친절하고 간결하게 답변한다.
                필요하면 매장 확인 후 안내드리겠다는 표현을 사용한다.
                300자 이내로 작성하고 답변 본문만 반환한다.

                가게 이름: %s
                문의 제목: %s""".formatted(storeName, inquiryTitle);
        try {
            return AiText.content(router.generate(AiTaskType.OWNER_REPLY_DRAFT, SYSTEM_PROMPT, prompt).content());
        } catch (AiClientException e) {
            return fallback("inquiryReply", e,
                    () -> templateFallback.inquiryReply(storeName, inquiryTitle));
        }
    }

    @Override
    public AiText complaintReply(String storeName, String keyword) {
        String prompt = """
                가게에 반복적으로 접수된 불만 키워드에 대한 사장님의 공지형 대응 문구를 작성한다.
                고객을 탓하지 않고, 사과와 개선 조치를 중심으로 작성한다.
                300자 이내로 작성하고 본문만 반환한다.

                가게 이름: %s
                반복 불만 키워드: %s""".formatted(storeName, keyword);
        try {
            return AiText.content(router.generate(AiTaskType.OWNER_REPLY_DRAFT, SYSTEM_PROMPT, prompt).content());
        } catch (AiClientException e) {
            return fallback("complaintReply", e,
                    () -> templateFallback.complaintReply(storeName, keyword));
        }
    }

    @Override
    public AiText marketingCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword) {
        String prompt = """
                지역 소상공인 가게의 이벤트 홍보 문구를 작성한다.
                과장 표현, 의학적 효능 표현, 허위 할인 표현을 피한다.
                이모지는 과하지 않게 최대 2개까지 사용한다.
                반드시 아래 JSON 형식으로만 응답한다. 다른 설명은 붙이지 않는다.
                {"title":"제목","content":"본문"}

                가게 이름: %s
                공지 유형: %s
                톤: %s
                핵심 키워드: %s""".formatted(
                storeName, noticeType.getDisplayName(), toneGuide(tone),
                keyword != null && !keyword.isBlank() ? keyword : "없음");
        try {
            String raw = router.generate(AiTaskType.EVENT_MARKETING_COPY, SYSTEM_PROMPT, prompt).content();
            return parseTitledText(raw, "[" + storeName + "] " + noticeType.getDisplayName());
        } catch (AiClientException e) {
            return fallback("marketingCopy", e,
                    () -> templateFallback.marketingCopy(storeName, noticeType, tone, keyword));
        }
    }

    @Override
    public AiText noticeCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword) {
        String prompt = """
                가게 공지사항으로 등록할 문구를 작성한다.
                휴무, 신메뉴, 이벤트, 운영시간 변경 등 목적에 맞게 작성한다.
                고객이 오해하지 않도록 날짜/시간 표현을 명확히 한다. 확실하지 않은 날짜는 쓰지 않는다.
                반드시 아래 JSON 형식으로만 응답한다. 다른 설명은 붙이지 않는다.
                {"title":"제목","content":"본문"}

                가게 이름: %s
                공지 유형: %s
                톤: %s
                핵심 키워드: %s""".formatted(
                storeName, noticeType.getDisplayName(), toneGuide(tone),
                keyword != null && !keyword.isBlank() ? keyword : "없음");
        try {
            String raw = router.generate(AiTaskType.STORE_NOTICE_DRAFT, SYSTEM_PROMPT, prompt).content();
            return parseTitledText(raw, "[" + storeName + "] " + noticeType.getDisplayName());
        } catch (AiClientException e) {
            return fallback("noticeCopy", e,
                    () -> templateFallback.noticeCopy(storeName, noticeType, tone, keyword));
        }
    }

    // ===================== 내부 유틸 =====================

    // JSON 파싱 성공 시 title/content 분리, 실패 시 기본 제목 + LLM 원문 content 사용
    AiText parseTitledText(String raw, String defaultTitle) {
        try {
            JsonNode node = objectMapper.readTree(stripCodeFence(raw));
            String title = node.path("title").asText(null);
            String content = node.path("content").asText(null);
            if (content != null && !content.isBlank()) {
                return AiText.of(title != null && !title.isBlank() ? title : defaultTitle, content.trim());
            }
        } catch (Exception ignored) {
            // JSON이 아니면 원문을 본문으로 사용
        }
        return AiText.of(defaultTitle, raw.trim());
    }

    // LLM이 ```json ... ``` 코드펜스로 감싸는 경우 제거
    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String toneGuide(AiTone tone) {
        return switch (tone) {
            case POLITE -> "정중하고 격식 있는 존댓말";
            case FRIENDLY -> "친근하고 편안한 말투";
            case SHORT -> "짧고 간결한 문장";
        };
    }

    private AiText fallback(String method, AiClientException e, java.util.function.Supplier<AiText> supplier) {
        log.warn("[AI-GENERATOR] LLM 생성 실패 — Template fallback 사용 (method={}): {}", method, e.getMessage());
        return supplier.get();
    }
}
