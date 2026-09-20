package com.eeum.eeum.application.order.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final int PAYMENT_PENDING_EXPIRE_MINUTES = 15;
    // 행별 Redis·DB 상태 전이를 수행하므로 5분 ShedLock 안에서 끝나도록 제한한다.
    private static final int EXPIRATION_BATCH_SIZE = 20;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "expirePaymentPendingOrders", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void expirePaymentPendingOrders() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(PAYMENT_PENDING_EXPIRE_MINUTES);

        List<Order> orders = orderRepository.findPaymentPendingOrdersBefore(
                OrderStatus.PENDING,
                PaymentStatus.PENDING,
                threshold,
                PageRequest.of(0, EXPIRATION_BATCH_SIZE,
                        Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("orderId")))
        );

        if (orders.isEmpty()) {
            return;
        }

        log.info("결제 대기 만료 대상 주문 수: {}", orders.size());

        for (Order order : orders) {
            try {
                orderService.expirePendingOrder(order.getOrderId());
            } catch (Exception e) {
                log.warn("결제 대기 주문 만료 처리 실패: orderId={}",
                        order.getOrderId(), e);
            }
        }
    }
}
