package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "중고 게시글 목록 응답")
public class UsedProductSummaryResponseDto {

    @Schema(description = "게시글 ID")
    private Long usedProductId;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "거래 유형")
    private UsedProductPriceType priceType;

    @Schema(description = "가격. NEGOTIABLE이면 null")
    private BigDecimal price;

    @Schema(description = "거래 상태")
    private UsedProductStatus status;

    @Schema(description = "거래 희망 동네")
    private String regionName;

    @Schema(description = "카테고리 이름")
    private String categoryName;

    @Schema(description = "대표 사진 URL. 사진이 없으면 null")
    private String thumbnailUrl;

    @Schema(description = "찜 수")
    private int favoriteCount;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    public static UsedProductSummaryResponseDto of(UsedProduct product, String thumbnailUrl) {
        return UsedProductSummaryResponseDto.builder()
                .usedProductId(product.getUsedProductId())
                .title(product.getTitle())
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .status(product.getStatus())
                .regionName(product.getRegion().getDong())
                .categoryName(product.getCategory().getName())
                .thumbnailUrl(thumbnailUrl)
                .favoriteCount(product.getFavoriteCount())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
