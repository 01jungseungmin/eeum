package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "챗봇 메시지 전송 요청 — 고정 질문 ID 또는 자유 입력 중 하나 필수")
public class AiChatMessageRequestDto {

    @Schema(description = "고정 질문 ID (1~8)", example = "1")
    private Integer quickQuestionId;

    @Size(max = 500)
    @Schema(description = "자유 입력 텍스트", example = "이번 주 이벤트 뭐 할까요?")
    private String text;
}
