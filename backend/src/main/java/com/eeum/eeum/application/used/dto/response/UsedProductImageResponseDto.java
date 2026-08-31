package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.used.entity.UsedProductImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "중고 게시글 사진 응답")
public class UsedProductImageResponseDto {

    @Schema(description = "이미지 ID")
    private Long imageId;

    @Schema(description = "이미지 URL")
    private String imageUrl;

    @Schema(description = "노출 순서 (1부터)")
    private int displayOrder;

    @Schema(description = "대표 사진 여부")
    private boolean thumbnail;

    public static UsedProductImageResponseDto from(UsedProductImage image) {
        return UsedProductImageResponseDto.builder()
                .imageId(image.getImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .thumbnail(image.isThumbnail())
                .build();
    }
}
