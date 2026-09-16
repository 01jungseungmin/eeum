package com.eeum.eeum.application.file.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 업로드 완료 확인 응답")
public record FileUploadConfirmResponseDto(
        @Schema(description = "검증된 DB 저장용 object key")
        String objectKey
) {
}
