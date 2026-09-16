package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** 채팅 화면에 붙는 중고 게시글 요약. */
@Getter
@Builder
@Schema(description = "채팅방에 연결된 중고 게시글 요약")
public class UsedProductChatSummaryDto {

    @Schema(description = "중고 게시글 ID", example = "25")
    private final Long usedProductId;

    @Schema(description = "게시글 제목", example = "자전거 팝니다")
    private final String title;

    @Schema(description = "대표 사진 URL. 사진이 없으면 null")
    private final String thumbnailUrl;

    @Schema(description = "거래 유형", example = "FIXED")
    private final UsedProductPriceType priceType;

    @Schema(description = "가격. NEGOTIABLE(가격제안)이면 null", example = "10000.00")
    private final BigDecimal price;

    @Schema(description = "거래 상태", example = "SELLING")
    private final UsedProductStatus status;

    /**
     * 게시글이 아직 공개 상태인지. false면 상세로 이동할 수 없다.
     *
     * 삭제만이 아니라 관리자 숨김과 판매자 탈퇴까지 함께 본다(isPubliclyVisible).
     * 삭제만 보면 숨겨진 게시글이 정상 글로 내려가 프론트가 이동을 막지 않고, 사용자는 404를 만난다.
     * 판정 축은 같은 성격의 UsedReviewResponseDto.usedProductVisible과 동일하게 맞춘다.
     */
    @Schema(description = "게시글 공개 여부. false면 상세로 이동할 수 없다 (삭제·숨김·판매자 탈퇴)",
            example = "true")
    private final boolean visible;

    public static UsedProductChatSummaryDto of(UsedProduct product, String thumbnailUrl) {
        return UsedProductChatSummaryDto.builder()
                .usedProductId(product.getUsedProductId())
                .title(product.getTitle())
                .thumbnailUrl(thumbnailUrl)
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .status(product.getStatus())
                .visible(product.isPubliclyVisible())
                .build();
    }
}
