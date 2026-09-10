package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 주문 결제만 사장 매출 원장으로 연결한다. AI 플랜 결제는 Order가 없으므로 이 진입점에 들어올 수 없다.
 */
@Service
@RequiredArgsConstructor
public class OwnerRevenueService {

    private static final BigDecimal ZERO_FEE = BigDecimal.ZERO;

    private final OwnerRevenueRepository ownerRevenueRepository;

    @Transactional
    public OwnerRevenue recordPaidOrder(Order order, Payment payment) {
        if (order == null || payment == null || payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }

        return ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseGet(() -> ownerRevenueRepository.save(OwnerRevenue.create(
                        order,
                        payment,
                        payment.getAmount(),
                        ZERO_FEE,
                        ZERO_FEE,
                        payment.getAmount()
                )));
    }

    @Transactional
    public void markOrderCompleted(Order order) {
        if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        OwnerRevenue revenue = ownerRevenueRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        revenue.markSettleableAtFromCompletedOrder();
    }
}
