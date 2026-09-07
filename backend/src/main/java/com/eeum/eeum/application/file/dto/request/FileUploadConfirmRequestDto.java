package com.eeum.eeum.application.file.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "S3 업로드 완료 확인 요청")
public record FileUploadConfirmRequestDto(
        @NotBlank
        @Size(max = 500)
        @Schema(description = "Presigned URL 발급 응답에서 받은 임시 object key", example = "tmp/used/42/550e8400-e29b-41d4-a716-446655440000.webp")
        String objectKey
) {
}
