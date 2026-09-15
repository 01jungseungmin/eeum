package com.eeum.eeum.application.file.dto.request;

import com.eeum.eeum.application.file.FileUploadPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "S3 이미지 업로드 URL 발급 요청")
public record FilePresignedUrlRequestDto(
        @NotNull
        @Schema(description = "업로드 용도", example = "USED")
        FileUploadPurpose purpose,

        @NotNull
        @Size(max = 100)
        @Schema(description = "브라우저가 업로드할 이미지 MIME 타입", example = "image/webp")
        String contentType,

        @NotNull
        @Positive
        @Schema(description = "원본 파일 크기(byte)", example = "1048576")
        Long contentLength
) {
}
