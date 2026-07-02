package com.eeum.eeum.application.reservation.mapper;

import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.store.entity.Store;
import org.springframework.stereotype.Component;

@Component
public class VisitReservationMapper {
    public VisitReservationResponseDto toVisitReservationResponseDto(VisitReservation reservation) {
        Store store = reservation.getStore();
        Account account = reservation.getAccount();

        return VisitReservationResponseDto.builder()
                .visitReservationId(reservation.getVisitReservationId())
                .storeId(store.getStoreId())
                .storeName(store.getName())
                .storeAddress(store.getAddress())
                .storePhone(store.getPhone())
                .accountId(account.getAccountId())
                .customerName(account.getName())
                .customerPhone(account.getPhone())
                .visitDate(reservation.getVisitDate())
                .visitTime(reservation.getVisitTime())
                .partySize(reservation.getPartySize())
                .storeTableId(reservation.getStoreTable() != null ? reservation.getStoreTable().getStoreTableId() : null)
                .tableName(reservation.getStoreTable() != null ? reservation.getStoreTable().getTableName() : null)
                .tableCapacity(reservation.getStoreTable() != null ? reservation.getStoreTable().getCapacity() : null)
                .reservedStartAt(reservation.getReservedStartAt())
                .reservedEndAt(reservation.getReservedEndAt())
                .requestMessage(reservation.getRequestMessage())
                .rejectReason(reservation.getRejectReason())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .modifiedAt(reservation.getModifiedAt())
                .build();
    }
}
