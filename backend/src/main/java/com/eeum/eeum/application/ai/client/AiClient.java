package com.eeum.eeum.application.ai.client;

/**
 * LLM Provider 공통 추상화 — Gemini/Groq/Mock 구현체를 가진다.
 * 호출 실패(키 누락, 4xx/5xx, timeout, 파싱 실패)는 AiClientException으로 던지고,
 * fallback 판단은 AiModelRouter가 담당한다.
 */
public interface AiClient {

    AiClientResponse generate(AiClientRequest request);

    AiProviderType providerType();
}
