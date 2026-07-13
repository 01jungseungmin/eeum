package com.eeum.eeum.application.ai.client;

import com.eeum.eeum.application.ai.config.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Gemini generateContent API 클라이언트.
 * 실패(키 누락, 4xx/5xx, timeout, 파싱 실패)는 전부 AiClientException으로 변환한다.
 * 주의: 로그에 API Key를 절대 출력하지 않는다.
 */
@Slf4j
@Component
public class GeminiAiClient implements AiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProviderProperties properties;

    public GeminiAiClient(
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
        String apiKey = properties.gemini().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiClientException(AiProviderType.GEMINI, "Gemini API Key가 설정되지 않았습니다");
        }

        // Gemini는 system role을 별도로 두지 않아도 되므로 하나의 프롬프트로 합친다
        String mergedPrompt = mergePrompts(request.systemPrompt(), request.userPrompt());
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", mergedPrompt)))),
                "generationConfig", Map.of(
                        "temperature", request.temperature() != null ? request.temperature() : 0.7,
                        "maxOutputTokens", request.maxOutputTokens() != null ? request.maxOutputTokens() : 512,
                        "thinkingConfig", Map.of(
                                "thinkingBudget", 0
                        )
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri(properties.gemini().baseUrl() + "/models/" + request.model()
                            + ":generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String content = extractText(rawResponse);
            return new AiClientResponse(AiProviderType.GEMINI, request.model(), content);
        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            // cause를 그대로 넘기면 RestClient가 URL(?key=...)을 예외 메시지에 포함시킬 수 있으므로 제거
            log.debug("Gemini 호출 실패 상세: {}", e.toString());
            throw new AiClientException(AiProviderType.GEMINI,
                    "Gemini 호출 실패: " + e.getClass().getSimpleName());
        }
    }

    // 첫 번째 candidate의 text 추출 — 비어 있으면 예외 (테스트를 위해 package-private)
    String extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidate = root.path("candidates").path(0);
            String finishReason = candidate.path("finishReason").asText(null);

            JsonNode textNode = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            String text = textNode.asText(null);
            // 고객 리뷰 답변/문의 답변/마케팅 문구 등 실제 생성 문구 전문은 로그에 남기지 않는다.
            log.info("[AI-CLIENT][GEMINI] finishReason={}, contentLength={}",
                    finishReason, text != null ? text.length() : 0);
            log.debug("[AI-CLIENT][GEMINI] responsePreview={}", preview(text));
            if (text == null || text.isBlank()) {
                throw new AiClientException(AiProviderType.GEMINI, "Gemini 응답이 비어 있습니다");
            }
            return text.trim();
        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AiClientException(AiProviderType.GEMINI, "Gemini 응답 파싱 실패", e);
        }
    }

    // 디버그 로그용 미리보기 — 전문을 남기지 않도록 앞부분만 자른다
    private String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "...";
    }

    private String mergePrompts(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return userPrompt;
        }
        return systemPrompt + "\n\n" + userPrompt;
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.GEMINI;
    }
}
