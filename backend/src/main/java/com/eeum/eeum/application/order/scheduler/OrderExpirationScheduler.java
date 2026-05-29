package com.eeum.eeum.application.order.scheduler;

import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedRate = 60000)
    public void expirePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        List<Order> orders = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING,
                threshold
        );

        orders.forEach(orderService::expirePendingOrder);
    }
}