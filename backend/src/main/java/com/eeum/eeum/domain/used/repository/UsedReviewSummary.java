package com.eeum.eeum.domain.used.repository;

/**
 * 판매자 평판 집계 원값.
 *
 * <p>후기가 없으면 {@code reviewCount}는 0, {@code averageRating}은 null이다 —
 * SQL {@code AVG}가 대상 행이 없을 때 null을 돌려준다. 0.0으로 바꾸지 않는다:
 * "별 0개"로 읽혀 후기 없는 판매자가 최악으로 보인다.
 */
public record UsedReviewSummary(Long reviewCount, Double averageRating) {
}
