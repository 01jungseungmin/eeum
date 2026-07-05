package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "store_visit_reservation_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreVisitReservationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long settingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "slot_interval_minutes", nullable = false)
    private Integer slotIntervalMinutes = 30;

    @Column(name = "same_day_reservation_allowed", nullable = false)
    private boolean sameDayReservationAllowed = true;

    @Column(name = "cancel_deadline_minutes", nullable = false)
    private Integer cancelDeadlineMinutes = 30;

    // 예약 가능 시작 시간 (기본 09:00)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(9, 0);

    // 예약 가능 종료 시간 — 이 시각 미만의 슬롯만 생성 (기본 18:00)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(18, 0);

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static StoreVisitReservationSetting createDefault(Store store) {
        StoreVisitReservationSetting setting = new StoreVisitReservationSetting();
        setting.store = store;
        return setting;
    }

    public void update(
            boolean enabled,
            Integer slotIntervalMinutes,
            boolean sameDayReservationAllowed,
            Integer cancelDeadlineMinutes,
            LocalTime startTime,
            LocalTime endTime
    ) {
        this.enabled = enabled;
        this.slotIntervalMinutes = slotIntervalMinutes;
        this.sameDayReservationAllowed = sameDayReservationAllowed;
        this.cancelDeadlineMinutes = cancelDeadlineMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}