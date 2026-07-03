package com.eeum.eeum.application.ai.router;

import com.eeum.eeum.application.ai.client.AiClient;
import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiClientRequest;
import com.eeum.eeum.application.ai.client.AiClientResponse;
import com.eeum.eeum.application.ai.client.AiProviderType;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.client.GeminiAiClient;
import com.eeum.eeum.application.ai.client.GroqAiClient;
import com.eeum.eeum.application.ai.client.MockAiClient;
import com.eeum.eeum.application.ai.config.AiProviderProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * taskType별 모델/파라미터 선택 + Provider 우선순위 호출.
 * Gemini 실패 → Groq fallback, 모든 Provider 실패 시에만 AiClientException을 던진다.
 * (Template 최종 fallback은 Generator 계층에서 처리 — 사용자 응답이 500이 되지 않도록)
 */
@Slf4j
@Component
public class AiModelRouter {

    private final AiProviderProperties properties;
    private final GeminiAiClient geminiAiClient;
    private final GroqAiClient groqAiClient;
    private final MockAiClient mockAiClient;

    public AiModelRouter(
            AiProviderProperties properties,
            GeminiAiClient geminiAiClient,
            GroqAiClient groqAiClient,
            MockAiClient mockAiClient
    ) {
        this.properties = properties;
        this.geminiAiClient = geminiAiClient;
        this.groqAiClient = groqAiClient;
        this.mockAiClient = mockAiClient;
    }

    public AiClientResponse generate(AiTaskType taskType, String systemPrompt, String userPrompt) {
        List<AiClient> chain = resolveChain();
        AiClientException lastFailure = null;

        for (AiClient client : chain) {
            AiClientRequest request = new AiClientRequest(
                    taskType,
                    modelFor(client.providerType(), taskType),
                    systemPrompt,
                    userPrompt,
                    temperatureFor(taskType),
                    maxTokensFor(taskType)
            );
            try {
                return client.generate(request);
            } catch (AiClientException e) {
                // fallback 판단용 실패 로그 — API Key는 절대 포함하지 않는다
                log.warn("[AI-ROUTER] {} 호출 실패 (task={}) — 다음 provider로 fallback: {}",
                        client.providerType(), taskType, e.getMessage());
                lastFailure = e;
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AiClientException(AiProviderType.TEMPLATE, "사용 가능한 AI Provider가 없습니다");
    }

    // provider 설정 기반 호출 체인 — mock이면 Mock 단독, gemini면 Gemini→Groq
    private List<AiClient> resolveChain() {
        // 발표 예비 모드 — 외부 호출 없이 Mock 단독 사용
        if ("mock".equalsIgnoreCase(properties.provider())) {
            return List.of(mockAiClient);
        }
        List<AiClient> chain = new ArrayList<>();
        addClient(chain, properties.provider());
        addClient(chain, properties.fallbackProvider());
        if (chain.isEmpty()) {
            chain.add(mockAiClient);
        }
        return chain;
    }

    private void addClient(List<AiClient> chain, String providerName) {
        if (providerName == null) {
            return;
        }
        switch (providerName.toLowerCase()) {
            case "gemini" -> chain.add(geminiAiClient);
            case "groq" -> chain.add(groqAiClient);
            default -> { /* template/mock 등은 별도 처리 */ }
        }
    }

    // 모델 라우팅: 답변 품질이 중요한 task는 상위 모델, 나머지는 경량 모델
    String modelFor(AiProviderType providerType, AiTaskType taskType) {
        boolean qualityTask = taskType == AiTaskType.OWNER_REPLY_DRAFT || taskType == AiTaskType.CHATBOT_REPLY;
        return switch (providerType) {
            case GEMINI -> qualityTask
                    ? properties.gemini().models().flash()
                    : properties.gemini().models().flashLite();
            case GROQ -> qualityTask
                    ? properties.groq().models().quality()
                    : properties.groq().models().fast();
            case MOCK, TEMPLATE -> "mock";
        };
    }

    Double temperatureFor(AiTaskType taskType) {
        return switch (taskType) {
            case PRODUCT_DESCRIPTION, EVENT_MARKETING_COPY, STORE_NOTICE_DRAFT -> 0.7;
            case OWNER_REPLY_DRAFT, CHATBOT_REPLY, INSIGHT_SUMMARY -> 0.5;
            case POLICY_CLASSIFICATION, OUTPUT_REVIEW -> 0.1;
        };
    }

    Integer maxTokensFor(AiTaskType taskType) {
        return switch (taskType) {
            case OWNER_REPLY_DRAFT, PRODUCT_DESCRIPTION -> 512;
            case EVENT_MARKETING_COPY, STORE_NOTICE_DRAFT, CHATBOT_REPLY -> 768;
            case POLICY_CLASSIFICATION, OUTPUT_REVIEW -> 256;
            case INSIGHT_SUMMARY -> 512;
        };
    }
}
