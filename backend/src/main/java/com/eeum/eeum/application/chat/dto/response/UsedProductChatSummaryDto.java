package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 채팅 화면에 붙는 중고 게시글 요약.
 *
 * <p>1:1 문의방은 이름을 두지 않으므로(상대와 상품이 방을 식별한다) 목록·상세 모두 이 값으로
 * "무엇에 대한 대화인지"를 표시한다. 이 값이 없으면 사용자는 방을 구분할 수 없다.
 *
 * <p>삭제된 게시글도 내려보낸다 — 기존 대화는 유지하는 정책이라, 프론트가 {@code deleted}로
 * "삭제된 게시글입니다"를 표시하고 상세 진입만 막을 수 있어야 한다.
 */
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

    @Schema(description = "삭제 여부. true면 게시글 상세로 이동할 수 없다", example = "false")
    private final boolean deleted;

    public static UsedProductChatSummaryDto of(UsedProduct product, String thumbnailUrl) {
        return UsedProductChatSummaryDto.builder()
                .usedProductId(product.getUsedProductId())
                .title(product.getTitle())
                .thumbnailUrl(thumbnailUrl)
                .priceType(product.getPriceType())
                .price(product.getPrice())
                .status(product.getStatus())
                .deleted(product.isDeleted())
                .build();
    }
}
