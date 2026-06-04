package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationSettingUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotItemRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationSettingResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationTimeSlotResponseDto;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitReservationSettingService {

    private final StoreRepository storeRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
    private final VisitReservationRepository visitReservationRepository;

    @Transactional(readOnly = true)
    public VisitReservationSettingResponseDto getSetting(Long ownerAccountId) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        return toSettingDto(setting);
    }

    @Transactional
    public VisitReservationSettingResponseDto updateSetting(
            Long ownerAccountId,
            VisitReservationSettingUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        setting.update(
                request.getEnabled(),
                request.getDefaultMaxVisitorCount(),
                request.getDefaultMaxTeamCount(),
                request.getSlotIntervalMinutes(),
                request.getSameDayReservationAllowed(),
                request.getCancelDeadlineMinutes()
        );

        return toSettingDto(setting);
    }

    @Transactional(readOnly = true)
    public List<VisitReservationTimeSlotResponseDto> getTimeSlots(
            Long ownerAccountId,
            LocalDate date
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        List<VisitReservationTimeSlot> slots = visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(
                        store.getStoreId(),
                        date
                );

        return slots.stream()
                .map(slot -> toTimeSlotDto(store, slot))
                .toList();
    }

    @Transactional
    public List<VisitReservationTimeSlotResponseDto> updateTimeSlots(
            Long ownerAccountId,
            VisitReservationTimeSlotUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        for (VisitReservationTimeSlotItemRequestDto item : request.getSlots()) {
            VisitReservationTimeSlot slot = visitReservationTimeSlotRepository
                    .findByStore_StoreIdAndSlotDateAndSlotTime(
                            store.getStoreId(),
                            request.getDate(),
                            item.getTime()
                    )
                    .orElseGet(() -> VisitReservationTimeSlot.create(
                            store,
                            request.getDate(),
                            item.getTime(),
                            item.getMaxVisitorCount(),
                            item.getMaxTeamCount()
                    ));

            slot.update(
                    item.getMaxVisitorCount(),
                    item.getMaxTeamCount(),
                    item.getEnabled()
            );

            visitReservationTimeSlotRepository.save(slot);
        }

        return visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(
                        store.getStoreId(),
                        request.getDate()
                )
                .stream()
                .sorted(Comparator.comparing(VisitReservationTimeSlot::getSlotTime))
                .map(slot -> toTimeSlotDto(store, slot))
                .toList();
    }

    private Store getOwnerStore(Long ownerAccountId) {
        return storeRepository.findByAccount_AccountId(ownerAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreVisitReservationSetting getSettingByStore(Store store) {
        return storeVisitReservationSettingRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));
    }

    private VisitReservationSettingResponseDto toSettingDto(
            StoreVisitReservationSetting setting
    ) {
        return VisitReservationSettingResponseDto.builder()
                .enabled(setting.isEnabled())
                .defaultMaxVisitorCount(setting.getDefaultMaxVisitorCount())
                .defaultMaxTeamCount(setting.getDefaultMaxTeamCount())
                .slotIntervalMinutes(setting.getSlotIntervalMinutes())
                .sameDayReservationAllowed(setting.isSameDayReservationAllowed())
                .cancelDeadlineMinutes(setting.getCancelDeadlineMinutes())
                .build();
    }

    private VisitReservationTimeSlotResponseDto toTimeSlotDto(
            Store store,
            VisitReservationTimeSlot slot
    ) {
        List<VisitReservationStatus> activeStatuses = List.of(
                VisitReservationStatus.PENDING,
                VisitReservationStatus.APPROVED
        );

        Integer reservedVisitorCount = visitReservationRepository.sumVisitorCountByTimeSlot(
                store.getStoreId(),
                slot.getSlotDate(),
                slot.getSlotTime(),
                activeStatuses
        );

        Long reservedTeamCount = visitReservationRepository
                .countByStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                        store.getStoreId(),
                        slot.getSlotDate(),
                        slot.getSlotTime(),
                        activeStatuses
                );

        return VisitReservationTimeSlotResponseDto.builder()
                .time(slot.getSlotTime())
                .maxVisitorCount(slot.getMaxVisitorCount())
                .maxTeamCount(slot.getMaxTeamCount())
                .reservedVisitorCount(reservedVisitorCount)
                .reservedTeamCount(reservedTeamCount)
                .enabled(slot.isEnabled())
                .build();
    }
}