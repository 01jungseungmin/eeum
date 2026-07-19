package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepositoryCustom {

    // 완료 주문 minOrderCount 회 이상인 단골 accountId 목록
    List<Long> findRegularAccountIds(Long storeId, OrderStatus status, long minOrderCount);

    // 단골이면서 inactiveSince 이후 주문이 없는 accountId 목록 (휴면 단골 판정)
    List<Long> findInactiveRegularAccountIds(Long storeId, OrderStatus status, long minOrderCount, LocalDateTime inactiveSince);

    // 단골 계정 수 집계 — ID 목록을 메모리에 올리지 않고 count만 반환
    long countRegularAccounts(Long storeId, OrderStatus status, long minOrderCount);
}
