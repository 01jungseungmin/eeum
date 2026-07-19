package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiClientResponse;
import com.eeum.eeum.application.ai.client.AiProviderType;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.router.AiModelRouter;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiTextGeneratorTest {

    @Mock private AiModelRouter router;

    private GeminiAiTextGenerator createGenerator() {
        return new GeminiAiTextGenerator(router, new TemplateAiTextGenerator(), new ObjectMapper());
    }

    private AiClientResponse response(String content) {
        return new AiClientResponse(AiProviderType.GEMINI, "gemini-2.5-flash", content);
    }

    @Test
    void 리뷰_답글_생성_시_LLM_응답_본문을_반환한다() {
        // given
        GeminiAiTextGenerator generator = createGenerator();
        when(router.generate(eq(AiTaskType.OWNER_REPLY_DRAFT), anyString(), anyString()))
                .thenReturn(response("소중한 리뷰 감사합니다. 다음에도 좋은 경험 드리겠습니다."));

        // when
        AiText result = generator.reviewReply("테스트 상점", 5, "맛있어요");

        // then
        assertThat(result.content()).isEqualTo("소중한 리뷰 감사합니다. 다음에도 좋은 경험 드리겠습니다.");
    }

    @Test
    void LLM_호출이_모두_실패하면_Template_응답으로_fallback한다() {
        // given
        GeminiAiTextGenerator generator = createGenerator();
        when(router.generate(any(), anyString(), anyString()))
                .thenThrow(new AiClientException(AiProviderType.GROQ, "모든 provider 실패"));

        // when
        AiText result = generator.reviewReply("테스트 상점", 5, "맛있어요");

        // then — Template 구현체와 동일한 결과 (사용자에게는 정상 응답)
        AiText templateResult = new TemplateAiTextGenerator().reviewReply("테스트 상점", 5, "맛있어요");
        assertThat(result.content()).isEqualTo(templateResult.content());
    }

    @Test
    void 마케팅_문구_JSON_응답은_title과_content로_분리된다() {
        // given
        GeminiAiTextGenerator generator = createGenerator();
        when(router.generate(eq(AiTaskType.EVENT_MARKETING_COPY), anyString(), anyString()))
                .thenReturn(response("""
                        {"title":"점심 할인 이벤트","content":"이번 주 점심 방문 시 10% 할인해 드려요."}"""));

        // when
        AiText result = generator.marketingCopy("테스트 상점", AiNoticeType.EVENT, AiTone.FRIENDLY, "점심");

        // then
        assertThat(result.title()).isEqualTo("점심 할인 이벤트");
        assertThat(result.content()).isEqualTo("이번 주 점심 방문 시 10% 할인해 드려요.");
    }

    @Test
    void 코드펜스로_감싼_JSON_응답도_파싱된다() {
        // given
        GeminiAiTextGenerator generator = createGenerator();
        when(router.generate(eq(AiTaskType.STORE_NOTICE_DRAFT), anyString(), anyString()))
                .thenReturn(response("""
                        ```json
                        {"title":"임시 휴무 안내","content":"금일 개인 사정으로 휴무합니다."}
                        ```"""));

        // when
        AiText result = generator.noticeCopy("테스트 상점", AiNoticeType.TEMP_CLOSED, AiTone.POLITE, null);

        // then
        assertThat(result.title()).isEqualTo("임시 휴무 안내");
        assertThat(result.content()).isEqualTo("금일 개인 사정으로 휴무합니다.");
    }

    @Test
    void JSON_파싱에_실패하면_기본_제목과_LLM_원문을_사용한다() {
        // given
        GeminiAiTextGenerator generator = createGenerator();
        when(router.generate(eq(AiTaskType.EVENT_MARKETING_COPY), anyString(), anyString()))
                .thenReturn(response("그냥 일반 텍스트로 온 홍보 문구입니다."));

        // when
        AiText result = generator.marketingCopy("테스트 상점", AiNoticeType.EVENT, AiTone.SHORT, null);

        // then
        assertThat(result.title()).contains("테스트 상점");
        assertThat(result.content()).isEqualTo("그냥 일반 텍스트로 온 홍보 문구입니다.");
    }
}
