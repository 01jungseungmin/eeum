package com.eeum.eeum.application.used.dto.response;

import com.eeum.eeum.domain.used.repository.UsedReviewSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 판매자 평판 요약.
 *
 * <p>후기 목록({@code Slice})과 분리한 이유는 생명주기가 다르기 때문이다. 요약은 프로필에
 * 진입할 때 한 번 필요하고, 목록은 무한 스크롤로 계속 이어진다. 목록 응답에 넣으면
 * 페이지마다 같은 값이 반복되고 집계 쿼리도 매번 나간다.
 */
@Getter
@Builder
@Schema(description = "판매자 평판 요약")
public class UsedReviewSummaryResponseDto {

    @Schema(description = "판매자 계정 ID", example = "7")
    private final Long sellerId;

    @Schema(description = "받은 후기 수", example = "12")
    private final long reviewCount;

    /**
     * 평균 별점. 후기가 없으면 null이다.
     *
     * <p>0.0으로 내리지 않는다 — "별 0개"로 읽혀 후기 없는 판매자가 최악으로 보인다.
     * 프론트는 null을 "후기 없음"으로 표시한다.
     */
    @Schema(description = "평균 별점(소수 1자리). 후기가 없으면 null", example = "4.3")
    private final BigDecimal averageRating;

    public static UsedReviewSummaryResponseDto of(Long sellerId, UsedReviewSummary summary) {
        Long count = summary.reviewCount() == null ? 0L : summary.reviewCount();
        return UsedReviewSummaryResponseDto.builder()
                .sellerId(sellerId)
                .reviewCount(count)
                // double을 그대로 내보내면 JSON에 4.299999...가 나간다.
                .averageRating(toDisplayRating(summary.averageRating()))
                .build();
    }

    private static BigDecimal toDisplayRating(Double average) {
        return average == null
                ? null
                : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }
}
