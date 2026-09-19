package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.enums.PaymentStatus;

import java.util.Collection;
import java.util.List;

public interface PaymentRepositoryCustom {

    // 관리자 대시보드 실시간 활동 — 최근 결제 완료 건. 취소·환불돼도 paidAt은 남으므로 상태로 거른다
    List<PaymentActivity> findRecentPaymentActivities(Collection<PaymentStatus> statuses, int limit);
}
