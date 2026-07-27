package com.eeum.eeum.application.ai.client;

public record AiClientResponse(
        AiProviderType providerType,
        String model,
        String content
) {
}
