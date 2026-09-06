package com.eeum.eeum.application.file.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "S3 Presigned POST URL 응답")
public record FilePresignedUrlResponseDto(
        @Schema(description = "DB에 저장할 object key", example = "used/42/550e8400-e29b-41d4-a716-446655440000.webp")
        String objectKey,

        @Schema(description = "S3에 multipart/form-data POST할 임시 URL")
        String uploadUrl,

        @Schema(description = "POST multipart/form-data에 반드시 포함할 필드")
        Map<String, String> formFields,

        @Schema(description = "업로드 HTTP 메서드", example = "POST")
        String uploadMethod,

        @Schema(description = "URL 만료 시각")
        Instant expiresAt
) {
}
