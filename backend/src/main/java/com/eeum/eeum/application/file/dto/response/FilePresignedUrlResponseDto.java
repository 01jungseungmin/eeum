package com.eeum.eeum.application.file.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "S3 Presigned PUT URL 응답")
public record FilePresignedUrlResponseDto(
        @Schema(description = "DB에 저장할 object key", example = "used/42/550e8400-e29b-41d4-a716-446655440000.webp")
        String objectKey,

        @Schema(description = "S3에 직접 PUT할 임시 URL")
        String uploadUrl,

        @Schema(description = "PUT 요청에 반드시 포함할 헤더")
        Map<String, String> headers,

        @Schema(description = "URL 만료 시각")
        Instant expiresAt
) {
}
