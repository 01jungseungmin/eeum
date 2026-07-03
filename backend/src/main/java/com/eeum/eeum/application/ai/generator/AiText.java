package com.eeum.eeum.application.ai.generator;

// AI 생성 결과 공통 타입 — 제목이 필요 없는 문구는 title = null
public record AiText(String title, String content) {

    public static AiText of(String title, String content) {
        return new AiText(title, content);
    }

    public static AiText content(String content) {
        return new AiText(null, content);
    }
}
