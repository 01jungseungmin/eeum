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
 * <p>비공개가 된 게시글도 내려보낸다 — 기존 대화는 유지하는 정책이라, 프론트가
 * {@code visible}로 "사라진 게시글입니다"를 표시하고 상세 진입만 막을 수 있어야 한다.
 *
 * <p>제목·가격은 감추지 않는다. 이 요약을 보는 사람은 참여자 검증을 통과한 거래 당사자
 * (구매자·판매자)뿐이고, 둘 다 그 게시글을 보고 대화를 시작한 사람이라 숨길 것이 없다.
 * 제3자에게 비공개 글의 제목을 감추는 것은 후기 목록의 몫이다({@code UsedReviewResponseDto}).
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

    /**
     * 게시글이 아직 공개 상태인지. false면 상세로 이동할 수 없다.
     *
     * <p>삭제만이 아니라 <b>관리자 숨김과 판매자 탈퇴까지</b> 함께 본다({@code isPubliclyVisible}).
     * 삭제만 보면 숨겨진 게시글이 정상 글로 내려가 프론트가 이동을 막지 않고, 사용자는 404를 만난다.
     * 판정 축은 같은 성격의 {@code UsedReviewResponseDto.usedProductVisible}과 동일하게 맞춘다.
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
