package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiChatActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "AI 챗봇 답변")
public class AiChatResponseDto {

    @Schema(description = "답변 본문")
    private final String text;

    @Schema(description = "액션 카드 목록")
    private final List<ChatActionDto> actions;

    @Schema(description = "범위 밖 질문 여부", example = "false")
    private final boolean outOfScope;

    @Schema(description = "이번 답변의 사용량 카운트 여부", example = "true")
    private final boolean usageCounted;


}
