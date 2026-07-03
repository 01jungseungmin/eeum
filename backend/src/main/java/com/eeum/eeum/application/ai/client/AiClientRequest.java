package com.eeum.eeum.application.ai.client;

public record AiClientRequest(
        AiTaskType taskType,
        String model,
        String systemPrompt,
        String userPrompt,
        Double temperature,
        Integer maxOutputTokens
) {
}
