package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationSettingUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotItemRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationSettingResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationTimeSlotResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;

    private static final List<VisitReservationStatus> ACTIVE_STATUSES =
            List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED);

    @Transactional(readOnly = true)
    public VisitReservationSettingResponseDto getSetting(Long ownerAccountId) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = getSettingByStore(store);
        return toSettingDto(setting);
    }

    public VisitReservationSettingResponseDto updateSetting(
            Long ownerAccountId,
            VisitReservationSettingUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        return redisLockService.executeWithLock(
                LockKeys.storeReservation(store.getStoreId()),
                Duration.ofSeconds(30),
                ErrorCode.LOCK_RESERVATION_FAILED,
                () -> transactionTemplate.execute(status -> {
                    validateSettingRequest(request);

                    StoreVisitReservationSetting setting = getSettingByStore(store);
                    setting.update(
                            request.getEnabled(),
                            request.getSlotIntervalMinutes(),
                            request.getSameDayReservationAllowed(),
                            request.getCancelDeadlineMinutes(),
                            request.getStartTime(),
                            request.getEndTime()
                    );
                    return toSettingDto(setting);
                })
        );
    }

    // 특정 날짜의 시간대 설정 목록 조회.
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

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    if (override != null) {
                        return toTimeSlotDto(override.getTimeSlotId(), time, override.isEnabled());
                    }
                    return toTimeSlotDto(null, time, true);
                })
                .toList();
    }

    // 특정 날짜의 시간대 설정 수정
    // enabled=true는 기본값과 동일하므로 오버라이드 레코드를 삭제한다.
    // enabled=false는 해당 날짜/시간만 닫는 오버라이드로 저장한다.
    public List<VisitReservationTimeSlotResponseDto> updateTimeSlots(
            Long ownerAccountId,
            VisitReservationTimeSlotUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        return redisLockService.executeWithLock(
                LockKeys.storeReservation(store.getStoreId()),
                Duration.ofSeconds(30),
                ErrorCode.LOCK_RESERVATION_FAILED,
                () -> transactionTemplate.execute(status -> updateTimeSlotsInternal(store, request))
        );
    }

    private List<VisitReservationTimeSlotResponseDto> updateTimeSlotsInternal(
            Store store,
            VisitReservationTimeSlotUpdateRequestDto request
    ) {
        StoreVisitReservationSetting setting = getSettingByStore(store);
        List<LocalTime> slotTimes = generateSlotTimes(setting);

        for (VisitReservationTimeSlotItemRequestDto item : request.getSlots()) {
            if (!slotTimes.contains(item.getTime())) {
                throw new BusinessException(ErrorCode.RESERVATION_INVALID_SLOT_TIME);
            }

            Optional<VisitReservationTimeSlot> existing =
                    visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(
                            store.getStoreId(), request.getDate(), item.getTime());

            if (Boolean.TRUE.equals(item.getEnabled())) {
                existing.ifPresent(visitReservationTimeSlotRepository::delete);
                continue;
            }

            validateNoActiveReservationOnSlot(
                    store.getStoreId(),
                    request.getDate(),
                    item.getTime(),
                    setting.getSlotIntervalMinutes()
            );

            VisitReservationTimeSlot slot = existing.orElseGet(() ->
                    VisitReservationTimeSlot.create(
                            store,
                            request.getDate(),
                            item.getTime(),
                            false
                    )
            );

            slot.update(false);
            visitReservationTimeSlotRepository.save(slot);
        }

        Map<LocalTime, VisitReservationTimeSlot> overrideMap = buildOverrideMap(store.getStoreId(), request.getDate());

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    if (override != null) {
                        return toTimeSlotDto(override.getTimeSlotId(), time, override.isEnabled());
                    }
                    return toTimeSlotDto(null, time, true);
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

    private void validateSettingRequest(VisitReservationSettingUpdateRequestDto request) {
        if (request.getSlotIntervalMinutes() == null || request.getSlotIntervalMinutes() <= 0) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_SLOT_TIME);
        }
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_SLOT_TIME);
        }
    }

    private void validateNoActiveReservationOnSlot(
            Long storeId,
            LocalDate date,
            LocalTime time,
            int interval
    ) {
        LocalDateTime startAt = LocalDateTime.of(date, time);
        LocalDateTime endAt = startAt.plusMinutes(interval);

        boolean hasActiveReservation = !visitReservationRepository
                .findOccupiedTableIds(storeId, startAt, endAt, ACTIVE_STATUSES)
                .isEmpty();

        if (hasActiveReservation) {
            throw new BusinessException(ErrorCode.RESERVATION_SLOT_CHANGE_NOT_ALLOWED);
        }
    }

    private List<LocalTime> generateSlotTimes(StoreVisitReservationSetting setting) {
        List<LocalTime> times = new ArrayList<>();
        int interval = setting.getSlotIntervalMinutes();
        if (interval <= 0) {
            return times;
        }
        // 하루 분(minute) 단위 정수로 순회 — LocalTime.plusMinutes의 자정 랩어라운드로 인한 무한 루프를 방지한다.
        // current + interval(슬롯 종료 시각)이 endMinute를 넘지 않는 슬롯만 생성.
        int startMinute = setting.getStartTime().toSecondOfDay() / 60;
        int endMinute = setting.getEndTime().toSecondOfDay() / 60;
        for (int minute = startMinute; minute + interval <= endMinute; minute += interval) {
            times.add(LocalTime.ofSecondOfDay(minute * 60L));
        }
        return times;
    }

    private Map<LocalTime, VisitReservationTimeSlot> buildOverrideMap(Long storeId, LocalDate date) {
        return visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(storeId, date)
                .stream()
                .collect(Collectors.toMap(VisitReservationTimeSlot::getSlotTime, s -> s));
    }

    private VisitReservationSettingResponseDto toSettingDto(StoreVisitReservationSetting setting) {
        return VisitReservationSettingResponseDto.builder()
                .enabled(setting.isEnabled())
                .slotIntervalMinutes(setting.getSlotIntervalMinutes())
                .sameDayReservationAllowed(setting.isSameDayReservationAllowed())
                .cancelDeadlineMinutes(setting.getCancelDeadlineMinutes())
                .startTime(setting.getStartTime())
                .endTime(setting.getEndTime())
                .build();
    }

    private VisitReservationTimeSlotResponseDto toTimeSlotDto(
            Long timeSlotId,
            LocalTime time,
            boolean enabled
    ) {
        return VisitReservationTimeSlotResponseDto.builder()
                .timeSlotId(timeSlotId)
                .time(time)
                .enabled(enabled)
                .closed(!enabled)
                .build();
    }
}
