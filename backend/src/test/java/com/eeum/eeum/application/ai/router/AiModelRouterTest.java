package com.eeum.eeum.application.ai.router;

import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiClientRequest;
import com.eeum.eeum.application.ai.client.AiClientResponse;
import com.eeum.eeum.application.ai.client.AiProviderType;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.client.GeminiAiClient;
import com.eeum.eeum.application.ai.client.GroqAiClient;
import com.eeum.eeum.application.ai.client.MockAiClient;
import com.eeum.eeum.application.ai.config.AiProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelRouterTest {

    @Mock private GeminiAiClient geminiAiClient;
    @Mock private GroqAiClient groqAiClient;

    private final MockAiClient mockAiClient = new MockAiClient();

    private AiProviderProperties properties(String provider) {
        return new AiProviderProperties(provider, "groq", "template", null, null);
    }

    private AiModelRouter createRouter(String provider) {
        return new AiModelRouter(properties(provider), geminiAiClient, groqAiClient, mockAiClient);
    }

    @Test
    void Gemini_성공_시_Groq를_호출하지_않는다() {
        // given
        AiModelRouter router = createRouter("gemini");
        when(geminiAiClient.generate(any())).thenReturn(
                new AiClientResponse(AiProviderType.GEMINI, "gemini-2.5-flash", "성공 응답"));
        when(geminiAiClient.providerType()).thenReturn(AiProviderType.GEMINI);

        // when
        AiClientResponse response = router.generate(AiTaskType.OWNER_REPLY_DRAFT, "system", "user");

        // then
        assertThat(response.providerType()).isEqualTo(AiProviderType.GEMINI);
        assertThat(response.content()).isEqualTo("성공 응답");
        verify(groqAiClient, never()).generate(any());
    }

    @Test
    void Gemini_실패_시_Groq로_fallback한다() {
        // given
        AiModelRouter router = createRouter("gemini");
        when(geminiAiClient.providerType()).thenReturn(AiProviderType.GEMINI);
        when(geminiAiClient.generate(any()))
                .thenThrow(new AiClientException(AiProviderType.GEMINI, "Gemini 호출 실패"));
        when(groqAiClient.providerType()).thenReturn(AiProviderType.GROQ);
        when(groqAiClient.generate(any())).thenReturn(
                new AiClientResponse(AiProviderType.GROQ, "llama-3.3-70b-versatile", "Groq fallback 응답"));

        // when
        AiClientResponse response = router.generate(AiTaskType.OWNER_REPLY_DRAFT, "system", "user");

        // then
        assertThat(response.providerType()).isEqualTo(AiProviderType.GROQ);
        assertThat(response.content()).isEqualTo("Groq fallback 응답");
    }

    @Test
    void Gemini와_Groq가_모두_실패하면_AiClientException이_발생한다() {
        // given
        AiModelRouter router = createRouter("gemini");
        when(geminiAiClient.providerType()).thenReturn(AiProviderType.GEMINI);
        when(geminiAiClient.generate(any()))
                .thenThrow(new AiClientException(AiProviderType.GEMINI, "Gemini 호출 실패"));
        when(groqAiClient.providerType()).thenReturn(AiProviderType.GROQ);
        when(groqAiClient.generate(any()))
                .thenThrow(new AiClientException(AiProviderType.GROQ, "Groq 호출 실패"));

        // when & then — 이 예외는 Generator 계층에서 잡아 Template fallback 처리한다
        assertThatThrownBy(() -> router.generate(AiTaskType.OWNER_REPLY_DRAFT, "system", "user"))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    void provider가_mock이면_외부_호출_없이_Mock_응답을_반환한다() {
        // given
        AiModelRouter router = createRouter("mock");

        // when
        AiClientResponse response = router.generate(AiTaskType.CHATBOT_REPLY, "system", "user");

        // then
        assertThat(response.providerType()).isEqualTo(AiProviderType.MOCK);
        assertThat(response.content()).isNotBlank();
        verify(geminiAiClient, never()).generate(any());
        verify(groqAiClient, never()).generate(any());
    }

    @Test
    void 사장_답변_초안은_상위_모델을_사용하고_마케팅_문구는_경량_모델을_사용한다() {
        // given
        AiModelRouter router = createRouter("gemini");

        // when & then
        assertThat(router.modelFor(AiProviderType.GEMINI, AiTaskType.OWNER_REPLY_DRAFT))
                .isEqualTo("gemini-2.5-flash");
        assertThat(router.modelFor(AiProviderType.GEMINI, AiTaskType.EVENT_MARKETING_COPY))
                .isEqualTo("gemini-2.5-flash-lite");
        assertThat(router.modelFor(AiProviderType.GROQ, AiTaskType.OWNER_REPLY_DRAFT))
                .isEqualTo("llama-3.3-70b-versatile");
        assertThat(router.modelFor(AiProviderType.GROQ, AiTaskType.POLICY_CLASSIFICATION))
                .isEqualTo("llama-3.1-8b-instant");
    }

    @Test
    void 정책_분류와_검수_task는_낮은_temperature가_적용된다() {
        // given
        AiModelRouter router = createRouter("gemini");

        // when & then
        assertThat(router.temperatureFor(AiTaskType.POLICY_CLASSIFICATION)).isEqualTo(0.1);
        assertThat(router.temperatureFor(AiTaskType.OUTPUT_REVIEW)).isEqualTo(0.1);
        assertThat(router.temperatureFor(AiTaskType.EVENT_MARKETING_COPY)).isEqualTo(0.7);
        assertThat(router.temperatureFor(AiTaskType.OWNER_REPLY_DRAFT)).isEqualTo(0.5);
    }

    @Test
    void 호출_요청에_taskType별_모델과_파라미터가_채워진다() {
        // given
        AiModelRouter router = createRouter("gemini");
        when(geminiAiClient.providerType()).thenReturn(AiProviderType.GEMINI);
        when(geminiAiClient.generate(any())).thenReturn(
                new AiClientResponse(AiProviderType.GEMINI, "gemini-2.5-flash-lite", "응답"));

        // when
        router.generate(AiTaskType.STORE_NOTICE_DRAFT, "system", "user");

        // then
        ArgumentCaptor<AiClientRequest> captor = ArgumentCaptor.forClass(AiClientRequest.class);
        verify(geminiAiClient).generate(captor.capture());
        AiClientRequest request = captor.getValue();
        assertThat(request.model()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(request.temperature()).isEqualTo(0.7);
        assertThat(request.maxOutputTokens()).isEqualTo(768);
    }
}
