package com.eeum.eeum.application.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상품 정보 응답")
public class ProductResponseDto {

    @Schema(description = "상품 ID", example = "1")
    private Long productId;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상품명", example = "김치찌개 반찬 세트")
    private String name;

    @Schema(description = "상품 설명", example = "직접 끓인 김치찌개와 반찬 세트입니다.")
    private String description;

    @Schema(description = "상품 기본 가격", example = "9000")
    private BigDecimal price;

    @Schema(description = "재고 수량", example = "15")
    private Integer stock;

    @Schema(description = "예약 가능 인원", example = "4")
    private Integer reservationCapacity;

    @Schema(description = "상품 유형", example = "SALE", allowableValues = {"SALE", "RESERVATION", "MENU"})
    private String productType;

    @Schema(description = "상품 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "SOLD_OUT", "INACTIVE"})
    private String status;

    @Schema(description = "상품 조회 수", example = "32")
    private Integer viewCount;

    @Schema(description = "상품 카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "상품 카테고리명", example = "국/찌개")
    private String categoryName;

    @Schema(description = "등록일시", example = "2026-05-25T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2026-05-25T13:00:00")
    private LocalDateTime modifiedAt;
}