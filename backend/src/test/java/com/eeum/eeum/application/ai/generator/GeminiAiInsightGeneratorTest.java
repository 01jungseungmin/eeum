package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiClientResponse;
import com.eeum.eeum.application.ai.client.AiProviderType;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.router.AiModelRouter;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiInsightGeneratorTest {

    @Mock private AiModelRouter router;

    private GeminiAiInsightGenerator createGenerator() {
        return new GeminiAiInsightGenerator(router, new TemplateAiInsightGenerator());
    }

    @Test
    void 이벤트_성과_요약은_계산된_숫자를_프롬프트에_담아_LLM에_전달한다() {
        // given
        GeminiAiInsightGenerator generator = createGenerator();
        when(router.generate(eq(AiTaskType.INSIGHT_SUMMARY), anyString(), anyString()))
                .thenReturn(new AiClientResponse(AiProviderType.GEMINI, "gemini-2.5-flash-lite",
                        "이벤트 기간 주문이 활발했습니다."));

        // when
        String summary = generator.eventPerformanceSummary("테스트 상점", 12, null, 0.4);

        // then — 숫자는 Service가 계산한 값 그대로 전달 (LLM이 추측하지 않도록)
        assertThat(summary).isEqualTo("이벤트 기간 주문이 활발했습니다.");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(router).generate(eq(AiTaskType.INSIGHT_SUMMARY), anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("12건").contains("40%");
    }

    @Test
    void LLM_실패_시_Template_분석_문구로_fallback한다() {
        // given
        GeminiAiInsightGenerator generator = createGenerator();
        when(router.generate(any(), anyString(), anyString()))
                .thenThrow(new AiClientException(AiProviderType.GROQ, "모든 provider 실패"));

        // when
        String judgement = generator.riskJudgement("테스트 상점", AiRiskLevel.NORMAL, false);

        // then
        String templateResult = new TemplateAiInsightGenerator().riskJudgement("테스트 상점", AiRiskLevel.NORMAL, false);
        assertThat(judgement).isEqualTo(templateResult);
    }

    @Test
    void 안전_체크리스트는_LLM을_호출하지_않고_Template을_사용한다() {
        // given
        GeminiAiInsightGenerator generator = createGenerator();

        // when
        var checklist = generator.safetyChecklist("테스트 상점");

        // then
        assertThat(checklist).isNotEmpty();
        verify(router, never()).generate(any(), anyString(), anyString());
    }
}
