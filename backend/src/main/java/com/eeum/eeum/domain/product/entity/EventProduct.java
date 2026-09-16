package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.product.enums.EventProductDisplayStatus;
import com.eeum.eeum.domain.product.enums.EventProductStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_product")
public class EventProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_product_id")
    private Long eventProductId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "event_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal eventPrice;

    @Column(name = "event_stock", nullable = false)
    private Integer eventStock;

    @Column(name = "sold_count", nullable = false)
    private Integer soldCount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventProductStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static EventProduct create(
            Product product,
            BigDecimal eventPrice,
            Integer eventStock,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        validatePeriod(startAt, endAt);

        EventProduct eventProduct = new EventProduct();
        eventProduct.product = product;
        eventProduct.eventPrice = eventPrice;
        eventProduct.eventStock = eventStock;
        eventProduct.soldCount = 0;
        eventProduct.startAt = startAt;
        eventProduct.endAt = endAt;
        eventProduct.status = EventProductStatus.ACTIVE;
        return eventProduct;
    }

    public void update(
            BigDecimal eventPrice,
            Integer eventStock,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        validatePeriod(startAt, endAt);

        this.eventPrice = eventPrice;
        this.eventStock = eventStock;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void end() {
        this.status = EventProductStatus.ENDED;
    }

    public void delete() {
        this.status = EventProductStatus.DELETED;
    }

    public EventProductDisplayStatus resolveDisplayStatus() {
        LocalDateTime now = LocalDateTime.now();

        if (this.status == EventProductStatus.ENDED
                || this.status == EventProductStatus.DELETED
                || now.isAfter(this.endAt)) {
            return EventProductDisplayStatus.ENDED;
        }

        if (getRemainingStock() <= 0) {
            return EventProductDisplayStatus.SOLD_OUT;
        }

        if (now.isBefore(this.startAt)) {
            return EventProductDisplayStatus.SCHEDULED;
        }

        return EventProductDisplayStatus.ONGOING;
    }

    public boolean isOngoing() {
        LocalDateTime now = LocalDateTime.now();
        return this.status == EventProductStatus.ACTIVE
                && !now.isBefore(startAt)
                && now.isBefore(endAt);
    }

    public int getRemainingStock() {
        return eventStock - soldCount;
    }

    public void decreaseStock(int quantity) {
        if (getRemainingStock() < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        this.soldCount += quantity;
    }

    private static void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        this.soldCount = Math.max(0, this.soldCount - quantity);
    }
}