package com.eeum.eeum.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "이미지 등록 요청")
public class ImageUploadRequestDto {

    @Schema(
            description = "S3 업로드 확정 API가 반환한 objectKey",
            example = "stores/42/550e8400-e29b-41d4-a716-446655440000.webp"
    )
    @NotBlank(message = "이미지 objectKey는 필수입니다.")
    @Size(max = 1000, message = "이미지 URL은 1000자 이하로 입력해야 합니다.")
    @JsonProperty("objectKey")
    @JsonAlias("imageUrl")
    private String imageUrl;

    @Schema(description = "대표 이미지 여부. true는 요청당 1개만 허용됩니다.", example = "true")
    private boolean isThumbnail = false;
}
