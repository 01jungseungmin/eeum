package com.eeum.eeum.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "이미지 응답")
public class ImageResponseDto {

    @Schema(description = "이미지 ID", example = "1")
    private Long imageId;

    @Schema(description = "이미지 URL", example = "https://example.com/images/store-1.jpg")
    private String imageUrl;

    @Schema(description = "이미지 노출 순서", example = "1")
    private int displayOrder;

    @Schema(description = "대표 이미지 여부", example = "true")
    private boolean isThumbnail;
}