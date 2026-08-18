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
@Schema(description = "중고 게시글 상세 응답")
public class UsedProductDetailResponseDto {

    @Schema(description = "게시글 ID")
    private Long usedProductId;

    @Schema(description = "판매자 계정 ID")
    private Long sellerId;

    @Schema(description = "판매자 닉네임")
    private String sellerNickname;

    @Schema(description = "카테고리 ID")
    private Long categoryId;

    @Schema(description = "카테고리 이름")
    private String categoryName;

    @Schema(description = "거래 희망 지역 ID")
    private Long regionId;

    @Schema(description = "거래 희망 동네 이름")
    private String regionName;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "본문")
    private String content;

    @Schema(description = "거래 유형")
    private UsedProductPriceType priceType;

    @Schema(description = "가격. NEGOTIABLE이면 null")
    private BigDecimal price;

    @Schema(description = "거래 상태")
    private UsedProductStatus status;

    @Schema(description = "관리자 숨김 여부. 작성자 본인에게만 노출된다")
    private boolean hidden;

    @Schema(description = "조회수")
    private int viewCount;

    @Schema(description = "찜 수")
    private int favoriteCount;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "최종 수정일시")
    private LocalDateTime modifiedAt;

    public static UsedProductDetailResponseDto from(UsedProduct product) {
        return UsedProductDetailResponseDto.builder()
                .usedProductId(product.getUsedProductId())
                .sellerId(product.getSeller().getAccountId())
                .sellerNickname(product.getSeller().getNickname())
                .categoryId(product.getCategory().getCategoryId())
                .categoryName(product.getCategory().getName())
                .regionId(product.getRegion().getRegionId())
                .regionName(product.getRegion().getDong())
                .title(product.getTitle())
                .content(product.getContent())
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .status(product.getStatus())
                .hidden(product.isHidden())
                .viewCount(product.getViewCount())
                .favoriteCount(product.getFavoriteCount())
                .createdAt(product.getCreatedAt())
                .modifiedAt(product.getModifiedAt())
                .build();
    }
}
