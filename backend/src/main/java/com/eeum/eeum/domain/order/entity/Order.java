package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "pickup_scheduled_at")
    private LocalDateTime pickupScheduledAt;

    @Column(name = "request_message", length = 500)
    private String requestMessage;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Order create(
            Account account,
            Store store,
            BigDecimal totalPrice,
            String orderNumber,
            OrderType orderType,
            LocalDateTime pickupScheduledAt,
            String requestMessage
    ) {
        Order order = new Order();
        order.account = account;
        order.store = store;
        order.totalPrice = totalPrice;
        order.orderNumber = orderNumber;
        order.orderType = orderType;
        order.pickupScheduledAt = pickupScheduledAt;
        order.requestMessage = requestMessage;
        order.status = OrderStatus.PENDING;
        return order;
    }

    public void markAsPaid() {
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void ready() {
        this.status = OrderStatus.READY;
        this.readyAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }

    public void expire() {
        this.status = OrderStatus.EXPIRED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = "결제 대기 시간 만료";
    }
}