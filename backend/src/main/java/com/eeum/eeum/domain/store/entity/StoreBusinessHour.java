package com.eeum.eeum.domain.store.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@Entity
@Table(name = "store_business_hour")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBusinessHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_business_hour_id")
    private Long storeBusinessHourId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private StoreDayOfWeek dayOfWeek;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    public static StoreBusinessHour create(
            Store store,
            StoreDayOfWeek dayOfWeek,
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime
    ) {
        StoreBusinessHour businessHour = new StoreBusinessHour();
        businessHour.store = store;
        businessHour.dayOfWeek = dayOfWeek;
        businessHour.update(closed, openTime, closeTime);
        return businessHour;
    }

    public void update(boolean closed, LocalTime openTime, LocalTime closeTime) {
        this.closed = closed;

        if (closed) {
            this.openTime = null;
            this.closeTime = null;
            return;
        }

        if (openTime == null || closeTime == null) {
            throw new IllegalArgumentException("영업일에는 오픈 시간과 마감 시간이 필요합니다.");
        }

        this.openTime = openTime;
        this.closeTime = closeTime;
    }
}