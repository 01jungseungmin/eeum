package com.eeum.eeum.domain.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 사장 통합 고객 목록에서 고객별 주문 통계를 배치 조회할 때 사용하는 프로젝션
public interface CustomerOrderStatProjection {
    Long getAccountId();
    Long getOrderCount();
    BigDecimal getTotalAmount();
    LocalDateTime getLastOrderedAt();
    Long getRecentOrderCount(); // 최근 6개월 내 완료 주문 수 (REGULAR 판정용)
}
