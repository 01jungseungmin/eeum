package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챗봇 고정 추천 질문")
public class AiChatQuickQuestionDto {

    @Schema(description = "질문 ID", example = "1")
    private final int id;

    @Schema(description = "질문 내용", example = "이번 주 이벤트 뭐 할까요?")
    private final String question;

    @Schema(description = "생성성 질문 여부 (월 사용량 카운트 대상)", example = "true")
    private final boolean generative;
}
