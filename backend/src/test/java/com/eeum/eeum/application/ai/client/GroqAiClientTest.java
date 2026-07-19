package com.eeum.eeum.application.ai.client;

import com.eeum.eeum.application.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GroqAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GroqAiClient createClient(String apiKey) {
        AiProviderProperties properties = new AiProviderProperties(
                "groq", null,
                null,
                new AiProviderProperties.Groq(apiKey, null, null));
        return new GroqAiClient(mock(RestClient.class), objectMapper, properties);
    }

    @Test
    void Groq_정상_응답에서_choices의_message_content를_추출한다() {
        // given
        GroqAiClient client = createClient("test-key");
        String response = """
                {"choices":[{"message":{"role":"assistant","content":"문의 답변 초안입니다."}}]}""";

        // when
        String content = client.extractContent(response);

        // then
        assertThat(content).isEqualTo("문의 답변 초안입니다.");
    }

    @Test
    void Groq_응답이_비어_있으면_AiClientException이_발생한다() {
        // given
        GroqAiClient client = createClient("test-key");
        String emptyResponse = """
                {"choices":[]}""";

        // when & then
        assertThatThrownBy(() -> client.extractContent(emptyResponse))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    void API_Key가_없으면_호출_없이_AiClientException이_발생한다() {
        // given
        GroqAiClient client = createClient("");
        AiClientRequest request = new AiClientRequest(
                AiTaskType.OWNER_REPLY_DRAFT, "llama-3.3-70b-versatile", "system", "user", 0.5, 512);

        // when & then
        assertThatThrownBy(() -> client.generate(request))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("API Key");
    }
}
