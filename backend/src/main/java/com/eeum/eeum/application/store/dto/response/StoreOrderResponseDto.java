package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.application.order.dto.response.OrderItemResponseDto;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class StoreOrderResponseDto {

    private Long orderId;
    private String customerNickname;
    private OrderStatus orderStatus;
    private BigDecimal totalPrice;
    private List<OrderItemResponseDto> items;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime readyAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static StoreOrderResponseDto of(Order order, List<OrderItem> orderItems) {
        return StoreOrderResponseDto.builder()
                .orderId(order.getOrderId())
                .customerNickname(order.getAccount().getNickname())
                .orderStatus(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .items(orderItems.stream()
                        .map(OrderItemResponseDto::from)
                        .toList())
                .paidAt(order.getPaidAt())
                .confirmedAt(order.getConfirmedAt())
                .readyAt(order.getReadyAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .createdAt(order.getCreatedAt())
                .modifiedAt(order.getModifiedAt())
                .build();
    }
}