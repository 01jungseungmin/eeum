package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class StoreDashboardResponseDto {

    // 상점 기본 정보
    private Long storeId;
    private String storeName;
    private StoreStatus storeStatus;
    private String thumbnailUrl;

    // 오늘 현황
    private long todayOrderCount;
    private BigDecimal todayRevenue;

    // 이번 달 현황
    private long monthOrderCount;
    private BigDecimal monthRevenue;

    // 대기 중인 처리 건수
    private long pendingOrderCount;
    private long pendingReservationCount;
    private long unansweredReviewCount;

    // 상품 현황
    private long totalProductCount;
    private long soldOutProductCount;

    // 평점
    private double averageRating;
    private long totalReviewCount;
}