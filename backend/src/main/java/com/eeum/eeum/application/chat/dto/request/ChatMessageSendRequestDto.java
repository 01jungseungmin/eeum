package com.eeum.eeum.application.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "텍스트 메시지 발송 요청 (REST 폴백)")
public class ChatMessageSendRequestDto {

    @Schema(description = "메시지 내용", example = "안녕하세요!")
    @NotBlank(message = "메시지 내용은 필수입니다")
    @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다")
    private String content;

    @Schema(description = "클라이언트 발급 메시지 UUID — 재전송 시 중복 차단용 (선택)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String clientMessageId;
}
