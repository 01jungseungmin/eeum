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
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotCountProjection;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
                request.getCancelDeadlineMinutes(),
                request.getStartTime(),
                request.getEndTime()
        );

        return toSettingDto(setting);
    }

    // 특정 날짜의 시간대 설정 목록 조회.
    // 1. 설정에서 슬롯 시간 목록 동적 생성 (DB 불필요)
    // 2. DB 예외(오버라이드) 슬롯 1쿼리 조회 예약 집계 배치 1쿼리 → 총 2쿼리 (N+1 없음)
    // 오버라이드가 없는 슬롯은 설정 기본값으로 표시, timeSlotId = null.
    @Transactional(readOnly = true)
    public List<VisitReservationTimeSlotResponseDto> getTimeSlots(
            Long ownerAccountId,
            LocalDate date
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        List<LocalTime> slotTimes = generateSlotTimes(setting);
        Map<LocalTime, VisitReservationTimeSlot> overrideMap = buildOverrideMap(store.getStoreId(), date);
        Map<LocalTime, VisitReservationTimeSlotCountProjection> countMap =
                buildCountMap(store.getStoreId(), date);

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    VisitReservationTimeSlotCountProjection count = countMap.get(time);
                    if (override != null) {
                        return toTimeSlotDto(
                                override.getTimeSlotId(), time,
                                override.getMaxVisitorCount(), override.getMaxTeamCount(),
                                override.isEnabled(), count);
                    }
                    // 오버라이드 없음 → 설정 기본값 사용, timeSlotId = null
                    return toTimeSlotDto(
                            null, time,
                            setting.getDefaultMaxVisitorCount(), setting.getDefaultMaxTeamCount(),
                            true, count);
                })
                .toList();
    }

    // 특정 날짜의 시간대 설정 수정
    // 기본값과 동일한 슬롯은 오버라이드 레코드를 삭제(또는 생성 안 함) 다른 슬롯만 DB에 저장
    @Transactional
    public List<VisitReservationTimeSlotResponseDto> updateTimeSlots(
            Long ownerAccountId,
            VisitReservationTimeSlotUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);

        for (VisitReservationTimeSlotItemRequestDto item : request.getSlots()) {
            boolean isDefault = item.getEnabled()
                    && item.getMaxVisitorCount().equals(setting.getDefaultMaxVisitorCount())
                    && item.getMaxTeamCount().equals(setting.getDefaultMaxTeamCount());

            Optional<VisitReservationTimeSlot> existing =
                    visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(
                            store.getStoreId(), request.getDate(), item.getTime());

            if (isDefault) {
                // 기본값과 동일 → 오버라이드 불필요, 기존 오버라이드가 있으면 삭제
                existing.ifPresent(visitReservationTimeSlotRepository::delete);
            } else {
                // 기본값과 다름 → 오버라이드 upsert
                VisitReservationTimeSlot slot = existing.orElseGet(() ->
                        VisitReservationTimeSlot.create(
                                store, request.getDate(), item.getTime(),
                                item.getMaxVisitorCount(), item.getMaxTeamCount(),
                                item.getEnabled()));
                slot.update(item.getMaxVisitorCount(), item.getMaxTeamCount(), item.getEnabled());
                visitReservationTimeSlotRepository.save(slot);
            }
        }

        // 변경 후 최신 상태 반환 (generate + overlay)
        List<LocalTime> slotTimes = generateSlotTimes(setting);
        Map<LocalTime, VisitReservationTimeSlot> overrideMap = buildOverrideMap(store.getStoreId(), request.getDate());
        Map<LocalTime, VisitReservationTimeSlotCountProjection> countMap =
                buildCountMap(store.getStoreId(), request.getDate());

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    VisitReservationTimeSlotCountProjection count = countMap.get(time);
                    if (override != null) {
                        return toTimeSlotDto(
                                override.getTimeSlotId(), time,
                                override.getMaxVisitorCount(), override.getMaxTeamCount(),
                                override.isEnabled(), count);
                    }
                    return toTimeSlotDto(
                            null, time,
                            setting.getDefaultMaxVisitorCount(), setting.getDefaultMaxTeamCount(),
                            true, count);
                })
                .toList();
    }

    // ===================== 내부 유틸 =====================

    private Store getOwnerStore(Long ownerAccountId) {
        return storeRepository.findByAccount_AccountId(ownerAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreVisitReservationSetting getSettingByStore(Store store) {
        return storeVisitReservationSettingRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));
    }

    // 설정(startTime, endTime, slotIntervalMinutes)에서 슬롯 시각 목록을 동적으로 생성
    private List<LocalTime> generateSlotTimes(StoreVisitReservationSetting setting) {
        List<LocalTime> times = new ArrayList<>();
        LocalTime current = setting.getStartTime();
        LocalTime end = setting.getEndTime();
        int interval = setting.getSlotIntervalMinutes();
        while (current.isBefore(end)) {
            times.add(current);
            current = current.plusMinutes(interval);
        }
        return times;
    }

    // DB에 저장된 오버라이드 슬롯을 Map<슬롯시각, 슬롯>으로 반환 (1쿼리)
    private Map<LocalTime, VisitReservationTimeSlot> buildOverrideMap(Long storeId, LocalDate date) {
        return visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(storeId, date)
                .stream()
                .collect(Collectors.toMap(VisitReservationTimeSlot::getSlotTime, s -> s));
    }

    // PENDING,APPROVED 예약을 시간대별로 배치 집계 → Map 반환 (1쿼리)
    private Map<LocalTime, VisitReservationTimeSlotCountProjection> buildCountMap(
            Long storeId, LocalDate date
    ) {
        return visitReservationRepository
                .countTimeSlotsByStoreAndDate(
                        storeId, date,
                        List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED))
                .stream()
                .collect(Collectors.toMap(
                        VisitReservationTimeSlotCountProjection::getReservationTime, p -> p));
    }

    private VisitReservationSettingResponseDto toSettingDto(StoreVisitReservationSetting setting) {
        return VisitReservationSettingResponseDto.builder()
                .enabled(setting.isEnabled())
                .defaultMaxVisitorCount(setting.getDefaultMaxVisitorCount())
                .defaultMaxTeamCount(setting.getDefaultMaxTeamCount())
                .slotIntervalMinutes(setting.getSlotIntervalMinutes())
                .sameDayReservationAllowed(setting.isSameDayReservationAllowed())
                .cancelDeadlineMinutes(setting.getCancelDeadlineMinutes())
                .startTime(setting.getStartTime())
                .endTime(setting.getEndTime())
                .build();
    }

    // 슬롯 데이터 + 미리 집계된 카운트 → DTO 변환(closed = !enabled OR 잔여팀 ≤ 0 OR 잔여인원 ≤ 0)
    // @param timeSlotId DB 오버라이드 슬롯 ID (기본 슬롯이면 null)
    private VisitReservationTimeSlotResponseDto toTimeSlotDto(
            Long timeSlotId,
            LocalTime time,
            int maxVisitorCount,
            int maxTeamCount,
            boolean enabled,
            VisitReservationTimeSlotCountProjection count
    ) {
        int reservedVisitorCount = count == null ? 0 : count.getReservedPeople().intValue();
        long reservedTeamCount   = count == null ? 0L : count.getReservedTeams();

        boolean closed = !enabled
                || reservedTeamCount   >= maxTeamCount
                || reservedVisitorCount >= maxVisitorCount;

        return VisitReservationTimeSlotResponseDto.builder()
                .timeSlotId(timeSlotId)
                .time(time)
                .maxVisitorCount(maxVisitorCount)
                .maxTeamCount(maxTeamCount)
                .reservedVisitorCount(reservedVisitorCount)
                .reservedTeamCount(reservedTeamCount)
                .enabled(enabled)
                .closed(closed)
                .build();
    }
}
