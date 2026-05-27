package com.eeum.eeum.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "이미지 다중 등록 요청")
public class ImageUploadListRequestDto {

    @Valid
    @NotEmpty(message = "이미지는 1장 이상 등록해야 합니다.")
    @Size(max = 20, message = "이미지는 한 번에 최대 20장까지 등록할 수 있습니다.")
    @Schema(description = "등록할 이미지 목록")
    private List<ImageUploadRequestDto> images;
}