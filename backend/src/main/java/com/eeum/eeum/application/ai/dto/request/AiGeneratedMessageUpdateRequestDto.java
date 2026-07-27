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
@Schema(description = "AI 생성 메시지 수정 요청")
public class AiGeneratedMessageUpdateRequestDto {

    @Size(max = 200)
    @Schema(description = "수정할 제목 (null이면 유지)")
    private String title;

    @Size(max = 2000)
    @Schema(description = "수정할 본문 (null이면 유지)")
    private String content;
}
