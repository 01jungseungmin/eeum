package com.eeum.eeum.application.ai.client;

// 외부 LLM API 호출 실패(키 누락, 4xx/5xx, timeout, 응답 파싱 실패)를 표현 — Router/Generator에서 잡아 fallback 처리
public class AiClientException extends RuntimeException {

    private final AiProviderType providerType;

    public AiClientException(AiProviderType providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    public AiClientException(AiProviderType providerType, String message, Throwable cause) {
        super(message, cause);
        this.providerType = providerType;
    }

    public AiProviderType getProviderType() {
        return providerType;
    }
}
