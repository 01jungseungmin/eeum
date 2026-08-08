package com.eeum.eeum.application.product.dto.response;

import com.eeum.eeum.domain.product.entity.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "사용자용 상품 목록 응답")
public class ProductListResponseDto {

    @Schema(description = "상품 ID", example = "1")
    private Long productId;

    @Schema(description = "상품명", example = "김치찌개 밀키트")
    private String name;

    @Schema(description = "상품 설명", example = "집에서 바로 끓여 먹을 수 있는 김치찌개 밀키트입니다.")
    private String description;

    @Schema(description = "상품 기본 가격", example = "9000")
    private BigDecimal price;

    @Schema(description = "상품 재고 수량. null이면 수량 제한 없음", example = "20")
    private Integer stock;

    @Schema(description = "상품 유형", example = "SALE", allowableValues = {"SALE", "RESERVATION", "MENU"})
    private String productType;

    @Schema(description = "상품 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "SOLD_OUT", "INACTIVE"})
    private String status;

    @Schema(description = "상품 대표 이미지 URL", example = "https://example.com/images/product-thumbnail.jpg")
    private String thumbnailUrl;

    @Schema(description = "진행 중인 이벤트 가격. 이벤트가 없으면 null", example = "7900")
    private BigDecimal eventPrice;

    @Schema(description = "진행 중인 이벤트 존재 여부", example = "true")
    private boolean hasEvent;

    @Schema(description = "상품 카테고리 Id", example = "1")
    private Long productCategoryId;
}