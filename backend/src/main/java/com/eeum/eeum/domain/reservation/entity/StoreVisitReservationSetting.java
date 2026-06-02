package com.eeum.eeum.domain.reservation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "default_max_visitor_count", nullable = false)
    private Integer defaultMaxVisitorCount = 5;

    @Column(name = "default_max_team_count", nullable = false)
    private Integer defaultMaxTeamCount = 1;

    @Column(name = "slot_interval_minutes", nullable = false)
    private Integer slotIntervalMinutes = 30;

    @Column(name = "same_day_reservation_allowed", nullable = false)
    private boolean sameDayReservationAllowed = true;

    @Column(name = "cancel_deadline_minutes", nullable = false)
    private Integer cancelDeadlineMinutes = 30;

    public static StoreVisitReservationSetting createDefault(Store store) {
        StoreVisitReservationSetting setting = new StoreVisitReservationSetting();
        setting.store = store;
        return setting;
    }

    public void update(
            boolean enabled,
            Integer defaultMaxVisitorCount,
            Integer defaultMaxTeamCount,
            Integer slotIntervalMinutes,
            boolean sameDayReservationAllowed,
            Integer cancelDeadlineMinutes
    ) {
        this.enabled = enabled;
        this.defaultMaxVisitorCount = defaultMaxVisitorCount;
        this.defaultMaxTeamCount = defaultMaxTeamCount;
        this.slotIntervalMinutes = slotIntervalMinutes;
        this.sameDayReservationAllowed = sameDayReservationAllowed;
        this.cancelDeadlineMinutes = cancelDeadlineMinutes;
    }
}