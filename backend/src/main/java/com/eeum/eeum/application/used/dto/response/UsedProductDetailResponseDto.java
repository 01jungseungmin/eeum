package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // 공개되는 "대략" 위치다. 정확한 약속 장소는 여기가 아니라 채팅에서 정한다.
    // 장소를 지정하지 않은 글은 넷 다 null이고, 클라이언트는 regionName만으로 표시한다.

    @Schema(description = "거래 장소명. 지정하지 않았으면 null", example = "역삼동 주민센터 앞")
    private String tradeLocationName;

    @Schema(description = "거래 장소 위도. 지정하지 않았으면 null", example = "37.500123")
    private Double tradeLatitude;

    @Schema(description = "거래 장소 경도. 지정하지 않았으면 null", example = "127.036456")
    private Double tradeLongitude;

    @Schema(description = "카카오 장소 ID. 검색을 거치지 않았으면 null", example = "26338954")
    private String tradePlaceId;

    /**
     * 지정된 거래 상대. <b>판매자와 그 상대에게만</b> 내려간다 — 제3자에게는 null이다.
     *
     * <p>이 값이 후기 작성 자격의 근거다. 판매완료 시 구매자를 생략하면 예약 때 지정한 상대가
     * 유지되므로, 클라이언트가 그 상대를 읽을 수단이 없으면 생략이 안전한지 판단할 수 없다.
     */
    @Schema(description = "지정된 거래 상대의 계정 ID. 판매자·본인에게만 내려간다", example = "42")
    private final Long buyerAccountId;

    @Schema(description = "지정된 거래 상대의 표시명. 판매자·본인에게만 내려간다", example = "구매자닉")
    private final String buyerNickname;

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

    @Schema(description = "사진 목록. 노출 순서대로 정렬된다")
    private List<UsedProductImageResponseDto> images;

    /**
     * @param viewerId 조회 주체. 비회원이면 null이다 — 거래 당사자에게만 상대를 보여주기 위해 받는다.
     */
    public static UsedProductDetailResponseDto from(
            UsedProduct product,
            List<UsedProductImageResponseDto> images,
            Long viewerId
    ) {
        // 지정된 상대는 거래 당사자에게만 보여준다. 제3자에게 내려보내면
        // "누가 무엇을 샀는지"가 게시글만으로 드러난다.
        Account buyer = product.getBuyer();
        boolean party = viewerId != null
                && (product.isOwnedBy(viewerId)
                    || (buyer != null && buyer.getAccountId().equals(viewerId)));
        Account visibleBuyer = party ? buyer : null;

        return UsedProductDetailResponseDto.builder()
                .buyerAccountId(visibleBuyer == null ? null : visibleBuyer.getAccountId())
                .buyerNickname(visibleBuyer == null ? null : visibleBuyer.getDisplayName())
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
                .tradeLocationName(product.getTradeLocationName())
                .tradeLatitude(product.getTradeLatitude())
                .tradeLongitude(product.getTradeLongitude())
                .tradePlaceId(product.getTradePlaceId())
                .hidden(product.isHidden())
                .viewCount(product.getViewCount())
                .favoriteCount(product.getFavoriteCount())
                .createdAt(product.getCreatedAt())
                .modifiedAt(product.getModifiedAt())
                .images(images)
                .build();
    }
}
