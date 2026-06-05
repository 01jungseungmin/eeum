package com.eeum.eeum.application.reservation.dto.response;

import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationLeftTimeSlotResponseDto {
    private Long timeSlotId;
    private LocalTime time;

    private int maxTeams;
    private int reservedTeams;
    private int leftTeams;

    private int maxPeople;
    private int reservedPeople;
    private int leftPeople;

    /** enabled=false이거나 팀·인원 정원 초과이면 true */
    private boolean closed;

    /** 슬롯 활성화 여부 (false면 사장이 직접 비활성화한 슬롯) */
    private boolean enabled;

    /**
     * @param timeSlotId DB 슬롯 ID
     * @param enabled    슬롯 활성화 여부 — false이면 closed도 강제 true
     */
    public static VisitReservationLeftTimeSlotResponseDto of(
            Long timeSlotId,
            LocalTime time,
            int maxTeams,
            int reservedTeams,
            int maxPeople,
            int reservedPeople,
            boolean enabled
    ) {
        int leftTeams  = Math.max(maxTeams  - reservedTeams,  0);
        int leftPeople = Math.max(maxPeople - reservedPeople, 0);

        boolean closed = !enabled || leftTeams <= 0 || leftPeople <= 0;

        return VisitReservationLeftTimeSlotResponseDto.builder()
                .timeSlotId(timeSlotId)
                .time(time)
                .maxTeams(maxTeams)
                .reservedTeams(reservedTeams)
                .leftTeams(leftTeams)
                .maxPeople(maxPeople)
                .reservedPeople(reservedPeople)
                .leftPeople(leftPeople)
                .closed(closed)
                .enabled(enabled)
                .build();
    }

    public static VisitReservationLeftTimeSlotResponseDto from(
            VisitReservationTimeSlot slot,
            Integer reservedVisitorCount,
            Long reservedTeamCount
    ) {
        int teams  = reservedTeamCount == null ? 0 : reservedTeamCount.intValue();
        int people = reservedVisitorCount == null ? 0 : reservedVisitorCount;

        int leftTeams  = Math.max(slot.getMaxTeamCount()    - teams,  0);
        int leftPeople = Math.max(slot.getMaxVisitorCount() - people, 0);

        boolean closed = !slot.isEnabled() || leftTeams <= 0 || leftPeople <= 0;

        return VisitReservationLeftTimeSlotResponseDto.builder()
                .timeSlotId(slot.getTimeSlotId())
                .time(slot.getSlotTime())
                .maxTeams(slot.getMaxTeamCount())
                .reservedTeams(teams)
                .leftTeams(leftTeams)
                .maxPeople(slot.getMaxVisitorCount())
                .reservedPeople(people)
                .leftPeople(leftPeople)
                .closed(closed)
                .enabled(slot.isEnabled())
                .build();
    }
}