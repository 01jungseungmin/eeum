package com.eeum.eeum.application.favorite.dto.response;

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
@Schema(description = "중고 게시글 찜 목록 응답")
public class FavoriteUsedProductResponseDto {

    @Schema(description = "찜 ID")
    private Long favoriteId;

    @Schema(description = "게시글 ID")
    private Long usedProductId;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "거래 유형")
    private UsedProductPriceType priceType;

    @Schema(description = "가격. NEGOTIABLE이면 null")
    private BigDecimal price;

    @Schema(description = "거래 상태. 판매완료 글도 찜 목록에 남는다")
    private UsedProductStatus status;

    @Schema(description = "거래 희망 동네")
    private String regionName;

    @Schema(description = "대표 사진 URL. 사진이 없으면 null")
    private String thumbnailUrl;

    @Schema(description = "찜 등록 일시")
    private LocalDateTime favoritedAt;

    public static FavoriteUsedProductResponseDto of(
            Long favoriteId, UsedProduct product, String thumbnailUrl, LocalDateTime favoritedAt) {
        return FavoriteUsedProductResponseDto.builder()
                .favoriteId(favoriteId)
                .usedProductId(product.getUsedProductId())
                .title(product.getTitle())
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .status(product.getStatus())
                .regionName(product.getRegion().getDong())
                .thumbnailUrl(thumbnailUrl)
                .favoritedAt(favoritedAt)
                .build();
    }
}
