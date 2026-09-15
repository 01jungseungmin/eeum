package com.eeum.eeum.application.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이미지 메시지 발송 요청 (S3 업로드 확정 후 objectKey 전달)")
public class ChatImageMessageSendRequestDto {

    @Schema(description = "S3 업로드 확정 API가 반환한 objectKey", example = "chat/42/550e8400-e29b-41d4-a716-446655440000.webp")
    @NotBlank(message = "이미지 objectKey는 필수입니다")
    @Size(max = 500, message = "이미지 objectKey는 500자 이하여야 합니다")
    @JsonProperty("objectKey")
    @JsonAlias("imageUrl")
    private String imageUrl;

    @Schema(description = "클라이언트 발급 메시지 UUID — 재전송 시 중복 차단용 (선택)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String clientMessageId;
}
