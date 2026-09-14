package com.eeum.eeum.domain.used.repository;

/**
 * 판매자 평판 집계 원값.
 *
 * 후기가 없으면 reviewCount는 0, averageRating은 null이다 —
 * SQL AVG가 대상 행이 없을 때 null을 돌려준다. 0.0으로 바꾸지 않는다:
 * "별 0개"로 읽혀 후기 없는 판매자가 최악으로 보인다.
 */
public record UsedReviewSummary(Long reviewCount, Double averageRating) {
}
