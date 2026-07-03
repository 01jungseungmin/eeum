package com.eeum.eeum.application.ai.client;

import com.eeum.eeum.application.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GeminiAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GeminiAiClient createClient(String apiKey) {
        AiProviderProperties properties = new AiProviderProperties(
                "gemini", "groq", "template",
                new AiProviderProperties.Gemini(apiKey, null, null),
                null);
        return new GeminiAiClient(mock(RestClient.class), objectMapper, properties);
    }

    @Test
    void Gemini_정상_응답에서_첫번째_candidate의_text를_추출한다() {
        // given
        GeminiAiClient client = createClient("test-key");
        String response = """
                {"candidates":[{"content":{"parts":[{"text":"안녕하세요 사장님, 답글 초안입니다."}],"role":"model"}}]}""";

        // when
        String text = client.extractText(response);

        // then
        assertThat(text).isEqualTo("안녕하세요 사장님, 답글 초안입니다.");
    }

    @Test
    void Gemini_응답이_비어_있으면_AiClientException이_발생한다() {
        // given
        GeminiAiClient client = createClient("test-key");
        String emptyResponse = """
                {"candidates":[]}""";

        // when & then
        assertThatThrownBy(() -> client.extractText(emptyResponse))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    void Gemini_응답이_JSON이_아니면_파싱_실패로_AiClientException이_발생한다() {
        // given
        GeminiAiClient client = createClient("test-key");

        // when & then
        assertThatThrownBy(() -> client.extractText("not-json"))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    void API_Key가_없으면_호출_없이_AiClientException이_발생한다() {
        // given
        GeminiAiClient client = createClient(null);
        AiClientRequest request = new AiClientRequest(
                AiTaskType.OWNER_REPLY_DRAFT, "gemini-2.5-flash", "system", "user", 0.5, 512);

        // when & then
        assertThatThrownBy(() -> client.generate(request))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("API Key");
    }
}
