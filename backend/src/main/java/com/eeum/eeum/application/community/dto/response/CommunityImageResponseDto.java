package com.eeum.eeum.application.community.dto.response;

import com.eeum.eeum.domain.community.entity.CommunityImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "커뮤니티 이미지 응답")
public class CommunityImageResponseDto {

    @Schema(description = "이미지 ID")
    private Long imageId;

    @Schema(description = "이미지 URL")
    private String imageUrl;

    @Schema(description = "노출 순서")
    private int displayOrder;

    public static CommunityImageResponseDto from(CommunityImage image) {
        return CommunityImageResponseDto.builder()
                .imageId(image.getImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
