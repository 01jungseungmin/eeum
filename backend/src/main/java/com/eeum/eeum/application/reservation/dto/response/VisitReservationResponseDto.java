package com.eeum.eeum.application.reservation.dto.response;

import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class VisitReservationResponseDto {

    private Long visitReservationId;

    private Long storeId;
    private String storeName;
    private String storeAddress;
    private String storePhone;

    private Long accountId;
    private String customerName;
    private String customerPhone;

    private LocalDate visitDate;
    private LocalTime visitTime;
    private Integer partySize;
    private Long storeTableId;
    private String tableName;
    private Integer tableCapacity;
    private LocalDateTime reservedStartAt;
    private LocalDateTime reservedEndAt;

    private String requestMessage;
    private String rejectReason;

    private VisitReservationStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}