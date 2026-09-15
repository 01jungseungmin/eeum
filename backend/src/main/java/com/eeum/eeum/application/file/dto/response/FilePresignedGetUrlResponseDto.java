package com.eeum.eeum.application.file.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "S3 Presigned GET URL 응답")
public record FilePresignedGetUrlResponseDto(
        @Schema(description = "일시적으로 유효한 조회 URL")
        String downloadUrl,
        @Schema(description = "URL 만료 시각")
        Instant expiresAt
) {
}
