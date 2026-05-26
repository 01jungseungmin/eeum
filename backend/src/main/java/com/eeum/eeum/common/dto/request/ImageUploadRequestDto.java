package com.eeum.eeum.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "이미지 등록 요청")
public class ImageUploadRequestDto {

    @Schema(
            description = "이미지 URL. 현재는 임시 외부 URL을 저장하고, 추후 S3 업로드 URL로 교체할 예정입니다.",
            example = "https://example.com/images/store-1.jpg"
    )
    @NotBlank(message = "이미지 URL은 필수입니다.")
    @Size(max = 1000, message = "이미지 URL은 1000자 이하로 입력해야 합니다.")
    private String imageUrl;
}