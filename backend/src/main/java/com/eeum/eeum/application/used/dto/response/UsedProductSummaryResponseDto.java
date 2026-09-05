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

    // 좌표는 일부러 뺐다. 목록은 지도를 그리지 않는데 좌표까지 내리면
    // 지도를 쓰지 않는 화면에까지 위치가 새어 나간다. 좌표가 필요하면 상세를 조회한다.
    @Schema(description = "거래 장소명. 지정하지 않았으면 null", example = "역삼동 주민센터 앞")
    private String tradeLocationName;

    @Schema(description = "카테고리 이름")
    private String categoryName;

    @Schema(description = "대표 사진 URL. 사진이 없으면 null")
    private String thumbnailUrl;

    @Schema(description = "찜 수")
    private int favoriteCount;

    // 조회수순 정렬의 커서 값이 된다 — 응답에 없으면 클라이언트가 다음 페이지를 요청할 수 없다.
    @Schema(description = "조회수")
    private int viewCount;

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
                .tradeLocationName(product.getTradeLocationName())
                .categoryName(product.getCategory().getName())
                .thumbnailUrl(thumbnailUrl)
                .favoriteCount(product.getFavoriteCount())
                .viewCount(product.getViewCount())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
