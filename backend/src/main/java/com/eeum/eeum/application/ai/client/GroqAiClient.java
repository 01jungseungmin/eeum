package com.eeum.eeum.application.ai.client;

import com.eeum.eeum.application.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groq OpenAI-compatible chat completions API 클라이언트.
 * 실패는 전부 AiClientException으로 변환해 Router가 fallback하도록 한다.
 * 주의: 로그에 API Key를 절대 출력하지 않는다.
 */
@Slf4j
@Component
public class GroqAiClient implements AiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProviderProperties properties;

    public GroqAiClient(
            @Qualifier("aiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            AiProviderProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiClientResponse generate(AiClientRequest request) {
        String apiKey = properties.groq().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiClientException(AiProviderType.GROQ, "Groq API Key가 설정되지 않았습니다");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        Map<String, Object> body = Map.of(
                "model", request.model(),
                "messages", messages,
                "temperature", request.temperature() != null ? request.temperature() : 0.7,
                "max_tokens", request.maxOutputTokens() != null ? request.maxOutputTokens() : 512
        );

        try {
            String rawResponse = restClient.post()
                    .uri(properties.groq().baseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String content = extractContent(rawResponse);
            return new AiClientResponse(AiProviderType.GROQ, request.model(), content);
        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AiClientException(AiProviderType.GROQ,
                    "Groq 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    // choices[0].message.content 추출 — 비어 있으면 예외 (테스트를 위해 package-private)
    String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.asText(null);
            if (content == null || content.isBlank()) {
                throw new AiClientException(AiProviderType.GROQ, "Groq 응답이 비어 있습니다");
            }
            return content.trim();
        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AiClientException(AiProviderType.GROQ, "Groq 응답 파싱 실패", e);
        }
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.GROQ;
    }
}
