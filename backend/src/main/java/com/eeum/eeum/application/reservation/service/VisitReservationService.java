package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationStatusUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.TableAvailabilityResponseDto;
import com.eeum.eeum.application.reservation.dto.response.TimeSlotAvailabilityResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationLeftTimeSlotResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.mapper.VisitReservationMapper;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.event.ReservationApprovedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationRejectedEvent;
import com.eeum.eeum.domain.reservation.repository.ReservationOccupancyProjection;
import com.eeum.eeum.domain.reservation.repository.StoreTableRepository;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReservationService {

    private final VisitReservationRepository visitReservationRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final StoreRepository storeRepository;
    private final AccountRepository accountRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
    private final StoreTableRepository storeTableRepository;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;
    private final VisitReservationMapper visitReservationMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<VisitReservationStatus> ACTIVE_STATUSES =
            List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED);

    // ===================== 사용자 예약 =====================

    public VisitReservationResponseDto createReservation(
            Long accountId,
            Long storeId,
            VisitReservationCreateRequestDto request
    ) {
        return redisLockService.executeWithLock(
                LockKeys.storeReservation(storeId),
                Duration.ofSeconds(30),
                ErrorCode.LOCK_RESERVATION_FAILED,
                () -> transactionTemplate.execute(status ->
                        createReservationInternal(accountId, storeId, request)
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<VisitReservationResponseDto> getMyReservations(Long accountId, Pageable pageable) {
        Page<VisitReservation> reservations = visitReservationRepository
                .findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(accountId, pageable);

        List<Long> reservationIds = reservations.getContent().stream()
                .map(VisitReservation::getVisitReservationId)
                .toList();
        Set<Long> reviewedReservationIds = reservationIds.isEmpty()
                ? Set.of()
                : storeReviewRepository.findVisitReservationIdsWithReview(reservationIds);

        return reservations.map(reservation -> visitReservationMapper.toVisitReservationResponseDto(
                reservation, reviewedReservationIds.contains(reservation.getVisitReservationId())));
    }

    @Transactional(readOnly = true)
    public VisitReservationResponseDto getMyReservationDetail(Long accountId, Long reservationId) {
        VisitReservation reservation = getReservation(reservationId);
        if (!reservation.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
        boolean hasReview = storeReviewRepository.existsByVisitReservation_VisitReservationId(reservationId);
        return visitReservationMapper.toVisitReservationResponseDto(reservation, hasReview);
    }

    @Transactional
    public void cancelMyReservation(Long accountId, Long reservationId) {
        VisitReservation reservation = getReservation(reservationId);
        if (!reservation.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
        if (reservation.getStatus() != VisitReservationStatus.PENDING
                && reservation.getStatus() != VisitReservationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
        }
        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(reservation.getStore().getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));
        LocalDateTime reservedStartAt = reservation.getReservedStartAt() != null
                ? reservation.getReservedStartAt()
                : LocalDateTime.of(reservation.getVisitDate(), reservation.getVisitTime());

        if (LocalDateTime.now().isAfter(reservedStartAt.minusMinutes(setting.getCancelDeadlineMinutes()))) {
            throw new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
        }
        reservation.cancel();
        log.info("방문 예약 취소: accountId={}, reservationId={}", accountId, reservationId);
    }

    // 사용자용 — 날짜 및 인원 수 기준 예약 가능 슬롯 조회
    @Transactional(readOnly = true)
    public List<TimeSlotAvailabilityResponseDto> getAvailableTimeSlots(
            Long storeId,
            LocalDate date,
            Integer partySize
    ) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() != StoreStatus.OPEN) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_NOT_RESERVABLE);
        }

        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));
        if (!setting.isEnabled()) {
            throw new BusinessException(ErrorCode.RESERVATION_DISABLED);
        }
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }
        if (!setting.isSameDayReservationAllowed() && date.isEqual(LocalDate.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(date.getDayOfWeek());
        StoreBusinessHour businessHour = storeBusinessHourRepository
                .findByStore_StoreIdAndDayOfWeek(storeId, dayOfWeek)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_STORE_BUSINESS_HOURS_NOT_SET));
        if (businessHour.isClosed()) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_CLOSED_DAY);
        }

        int duration = setting.getSlotIntervalMinutes();
        int effectivePartySize = resolvePartySize(partySize);
        List<LocalTime> slotTimes = generateSlotTimes(setting);

        boolean isToday = date.isEqual(LocalDate.now());
        LocalDateTime now = LocalDateTime.now();

        List<StoreTable> activeTables = storeTableRepository
                .findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId);
        List<ReservationOccupancyProjection> dayOccupancies = visitReservationRepository
                .findDayOccupancies(storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), ACTIVE_STATUSES);
        Map<LocalTime, VisitReservationTimeSlot> overrideMap =
                visitReservationTimeSlotRepository
                        .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(storeId, date)
                        .stream()
                        .collect(Collectors.toMap(VisitReservationTimeSlot::getSlotTime, s -> s));

        return slotTimes.stream()
                .filter(time -> LocalDateTime.of(date, time).isAfter(now))
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    if (override != null && !override.isEnabled()) {
                        return TimeSlotAvailabilityResponseDto.builder()
                                .time(time)
                                .available(false)
                                .availableTableCount(0)
                                .minAvailableCapacity(null)
                                .maxAvailableCapacity(null)
                                .tableAvailabilities(List.of())
                                .build();
                    }

                    LocalDateTime startAt = LocalDateTime.of(date, time);
                    LocalDateTime endAt = startAt.plusMinutes(duration);

                    Set<Long> occupiedIds = dayOccupancies.stream()
                            .filter(o -> o.getStartAt().isBefore(endAt) && o.getEndAt().isAfter(startAt))
                            .map(ReservationOccupancyProjection::getTableId)
                            .collect(Collectors.toSet());

                    List<StoreTable> reservableTables = activeTables.stream()
                            .filter(t -> t.getCapacity() >= effectivePartySize)
                            .toList();

                    List<StoreTable> available = reservableTables.stream()
                            .filter(t -> !occupiedIds.contains(t.getStoreTableId()))
                            .toList();

                    Map<Integer, Long> totalCountByCapacity = reservableTables.stream()
                            .collect(Collectors.groupingBy(
                                    StoreTable::getCapacity,
                                    TreeMap::new,
                                    Collectors.counting()
                            ));

                    Map<Integer, Long> reservedCountByCapacity = reservableTables.stream()
                            .filter(t -> occupiedIds.contains(t.getStoreTableId()))
                            .collect(Collectors.groupingBy(
                                    StoreTable::getCapacity,
                                    TreeMap::new,
                                    Collectors.counting()
                            ));

                    List<TableAvailabilityResponseDto> tableAvailabilities =
                            totalCountByCapacity.entrySet().stream()
                                    .map(entry -> {
                                        Integer capacity = entry.getKey();
                                        int totalCount = entry.getValue().intValue();
                                        int reservedCount = reservedCountByCapacity
                                                .getOrDefault(capacity, 0L)
                                                .intValue();

                                        return TableAvailabilityResponseDto.builder()
                                                .capacity(capacity)
                                                .totalCount(totalCount)
                                                .reservedCount(reservedCount)
                                                .availableCount(totalCount - reservedCount)
                                                .build();
                                    })
                                    .toList();

                    return TimeSlotAvailabilityResponseDto.builder()
                            .time(time)
                            .available(!available.isEmpty())
                            .availableTableCount(available.size())
                            .minAvailableCapacity(available.isEmpty() ? null : available.get(0).getCapacity())
                            .maxAvailableCapacity(available.isEmpty() ? null : available.get(available.size() - 1).getCapacity())
                            .tableAvailabilities(tableAvailabilities)
                            .build();
                })
                .toList();
    }

    // ===================== 사장 예약 관리 =====================

    @Transactional(readOnly = true)
    public Page<VisitReservationResponseDto> getOwnerReservations(
            Long ownerAccountId,
            VisitReservationStatus status,
            Pageable pageable
    ) {
        Store store = getOwnerStore(ownerAccountId);
        if (status == null) {
            return visitReservationRepository
                    .findByStore_StoreIdOrderByVisitDateDescVisitTimeDesc(store.getStoreId(), pageable)
                    .map(visitReservationMapper::toVisitReservationResponseDto);
        }
        return visitReservationRepository
                .findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(store.getStoreId(), status, pageable)
                .map(visitReservationMapper::toVisitReservationResponseDto);
    }

    @Transactional(readOnly = true)
    public VisitReservationResponseDto getOwnerReservationDetail(Long ownerAccountId, Long reservationId) {
        Store store = getOwnerStore(ownerAccountId);
        VisitReservation reservation = getReservation(reservationId);
        validateStoreOwner(store, reservation);
        return visitReservationMapper.toVisitReservationResponseDto(reservation);
    }

    @Transactional
    public void approveReservation(Long ownerAccountId, Long reservationId) {
        Store store = getOwnerStore(ownerAccountId);
        VisitReservation reservation = getReservation(reservationId);
        validateStoreOwner(store, reservation);
        if (reservation.getStatus() != VisitReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_APPROVE_NOT_ALLOWED);
        }
        reservation.approve();
        log.info("방문 예약 승인: ownerAccountId={}, reservationId={}", ownerAccountId, reservationId);
        eventPublisher.publishEvent(new ReservationApprovedEvent(
                reservation.getAccount().getAccountId(),
                reservation.getStore().getName(),
                reservation.getVisitDate(),
                reservation.getVisitTime(),
                reservation.getVisitReservationId()
        ));
    }

    @Transactional
    public void rejectReservation(Long ownerAccountId, Long reservationId, VisitReservationStatusUpdateRequestDto request) {
        Store store = getOwnerStore(ownerAccountId);
        VisitReservation reservation = getReservation(reservationId);
        validateStoreOwner(store, reservation);
        if (reservation.getStatus() != VisitReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_REJECT_NOT_ALLOWED);
        }
        reservation.reject(request.getRejectReason());
        log.info("방문 예약 거절: ownerAccountId={}, reservationId={}", ownerAccountId, reservationId);
        eventPublisher.publishEvent(new ReservationRejectedEvent(
                reservation.getAccount().getAccountId(),
                reservation.getStore().getName(),
                reservation.getVisitDate(),
                reservation.getVisitTime(),
                reservation.getRejectReason(),
                reservation.getVisitReservationId()
        ));
    }

    @Transactional
    public void completeReservation(Long ownerAccountId, Long reservationId) {
        Store store = getOwnerStore(ownerAccountId);
        VisitReservation reservation = getReservation(reservationId);
        validateStoreOwner(store, reservation);
        if (reservation.getStatus() != VisitReservationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.RESERVATION_COMPLETE_NOT_ALLOWED);
        }
        reservation.complete();
        log.info("방문 예약 완료: ownerAccountId={}, reservationId={}", ownerAccountId, reservationId);
    }

    // 사장용 날짜별 잔여 슬롯 현황 (테이블 기반)
    @Transactional(readOnly = true)
    public List<VisitReservationLeftTimeSlotResponseDto> getOwnerLeftTimeSlot(
            Long ownerAccountId, LocalDate date
    ) {
        Store store = getOwnerStore(ownerAccountId);
        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));

        Long storeId = store.getStoreId();
        int interval = setting.getSlotIntervalMinutes();
        List<LocalTime> slotTimes = generateSlotTimes(setting);

        List<StoreTable> allTables = storeTableRepository
                .findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId);
        int totalTableCount = allTables.size();

        Map<LocalTime, VisitReservationTimeSlot> overrideMap =
                visitReservationTimeSlotRepository
                        .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(storeId, date)
                        .stream()
                        .collect(Collectors.toMap(
                                VisitReservationTimeSlot::getSlotTime,
                                s -> s
                        ));

        List<ReservationOccupancyProjection> dayOccupancies = visitReservationRepository
                .findDayOccupancies(
                        storeId,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay(),
                        ACTIVE_STATUSES
                );

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);

                    LocalDateTime startAt = LocalDateTime.of(date, time);
                    LocalDateTime endAt = startAt.plusMinutes(interval);

                    Set<Long> occupiedIds = dayOccupancies.stream()
                            .filter(o -> o.getStartAt().isBefore(endAt)
                                    && o.getEndAt().isAfter(startAt))
                            .map(ReservationOccupancyProjection::getTableId)
                            .collect(Collectors.toSet());

                    int reservedTableCount = occupiedIds.size();

                    boolean slotDisabled = override != null && !override.isEnabled();

                    List<StoreTable> availableTables = slotDisabled
                            ? List.of()
                            : allTables.stream()
                            .filter(t -> !occupiedIds.contains(t.getStoreTableId()))
                            .toList();

                    List<TableAvailabilityResponseDto> tableAvailabilities =
                            buildTableAvailabilities(allTables, occupiedIds, slotDisabled);

                    return VisitReservationLeftTimeSlotResponseDto.builder()
                            .time(time)
                            .available(!slotDisabled && !availableTables.isEmpty())
                            .availableTableCount(availableTables.size())
                            .totalTableCount(totalTableCount)
                            .reservedTableCount(reservedTableCount)
                            .minAvailableCapacity(availableTables.isEmpty()
                                    ? null
                                    : availableTables.get(0).getCapacity())
                            .maxAvailableCapacity(availableTables.isEmpty()
                                    ? null
                                    : availableTables.get(availableTables.size() - 1).getCapacity())
                            .tableAvailabilities(tableAvailabilities)
                            .build();
                })
                .toList();
    }
    // ===================== 내부 유틸 =====================

    private VisitReservationResponseDto createReservationInternal(
            Long accountId, Long storeId, VisitReservationCreateRequestDto request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        validateReservableStore(store);
        validateVisitDateTime(request.getVisitDate(), request.getVisitTime());

        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));

        if (!setting.isEnabled()) {
            throw new BusinessException(ErrorCode.RESERVATION_DISABLED);
        }
        if (!setting.isSameDayReservationAllowed() && request.getVisitDate().isEqual(LocalDate.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        LocalDateTime startAt = LocalDateTime.of(request.getVisitDate(), request.getVisitTime());
        LocalDateTime endAt = startAt.plusMinutes(setting.getSlotIntervalMinutes());

        validateSlotInterval(request.getVisitTime(), setting.getStartTime(), setting.getSlotIntervalMinutes());
        validateBusinessHours(store, request.getVisitDate(), request.getVisitTime(), endAt.toLocalTime());
        validateSlotEnabled(storeId, request.getVisitDate(), request.getVisitTime());
        validateDuplicateMyReservation(accountId, store, request.getVisitDate(), request.getVisitTime());

        int partySize = resolvePartySize(request.getPartySize());

        StoreTable assignedTable = assignAvailableTable(storeId, partySize, startAt, endAt);

        VisitReservation reservation = VisitReservation.create(
                store, account,
                request.getVisitDate(), request.getVisitTime(),
                partySize, request.getRequestMessage(),
                assignedTable, startAt, endAt
        );
        visitReservationRepository.save(reservation);

        log.info("방문 예약 생성: accountId={}, storeId={}, reservationId={}, tableId={}",
                accountId, storeId, reservation.getVisitReservationId(), assignedTable.getStoreTableId());

        eventPublisher.publishEvent(new ReservationCreatedEvent(
                store.getAccount().getAccountId(),
                account.getName(),
                store.getName(),
                reservation.getVisitDate(),
                reservation.getVisitTime(),
                reservation.getVisitReservationId()
        ));

        return visitReservationMapper.toVisitReservationResponseDto(reservation);
    }

    private List<TableAvailabilityResponseDto> buildTableAvailabilities(
            List<StoreTable> tables,
            Set<Long> occupiedIds,
            boolean slotDisabled
    ) {
        Map<Integer, Long> totalCountByCapacity = tables.stream()
                .collect(Collectors.groupingBy(
                        StoreTable::getCapacity,
                        TreeMap::new,
                        Collectors.counting()
                ));

        Map<Integer, Long> reservedCountByCapacity = tables.stream()
                .filter(t -> occupiedIds.contains(t.getStoreTableId()))
                .collect(Collectors.groupingBy(
                        StoreTable::getCapacity,
                        TreeMap::new,
                        Collectors.counting()
                ));

        return totalCountByCapacity.entrySet().stream()
                .map(entry -> {
                    Integer capacity = entry.getKey();
                    int totalCount = entry.getValue().intValue();
                    int reservedCount = reservedCountByCapacity
                            .getOrDefault(capacity, 0L)
                            .intValue();

                    int availableCount = slotDisabled
                            ? 0
                            : totalCount - reservedCount;

                    return TableAvailabilityResponseDto.builder()
                            .capacity(capacity)
                            .totalCount(totalCount)
                            .reservedCount(reservedCount)
                            .availableCount(availableCount)
                            .build();
                })
                .toList();
    }

    private StoreTable assignAvailableTable(
            Long storeId, int partySize, LocalDateTime startAt, LocalDateTime endAt
    ) {
        List<Long> occupiedTableIds = visitReservationRepository.findOccupiedTableIds(
                storeId, startAt, endAt, ACTIVE_STATUSES);

        List<StoreTable> candidates;
        if (occupiedTableIds.isEmpty()) {
            candidates = storeTableRepository
                    .findByStore_StoreIdAndActiveTrueAndCapacityGreaterThanEqualOrderByCapacityAscStoreTableIdAsc(
                            storeId, partySize);
        } else {
            candidates = storeTableRepository.findAvailableTablesExcludingOccupied(
                    storeId, partySize, occupiedTableIds);
        }

        return candidates.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_TABLE_UNAVAILABLE));
    }

    private VisitReservation getReservation(Long reservationId) {
        return visitReservationRepository.findByVisitReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Store getOwnerStore(Long ownerAccountId) {
        return storeRepository.findByAccount_AccountId(ownerAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private void validateStoreOwner(Store store, VisitReservation reservation) {
        if (!reservation.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
    }

    private void validateReservableStore(Store store) {
        if (store.getStatus() != StoreStatus.OPEN) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_NOT_RESERVABLE);
        }
    }

    private void validateVisitDateTime(LocalDate visitDate, LocalTime visitTime) {
        if (visitDate == null || visitTime == null) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime visitDateTime = LocalDateTime.of(visitDate, visitTime);

        if (!visitDateTime.isAfter(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }
    }

    private void validateSlotEnabled(Long storeId, LocalDate visitDate, LocalTime visitTime) {
        visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateAndSlotTime(storeId, visitDate, visitTime)
                .ifPresent(slot -> {
                    if (!slot.isEnabled()) {
                        throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
                    }
                });
    }

    private void validateSlotInterval(LocalTime visitTime, LocalTime startTime, int slotIntervalMinutes) {
        long diffMinutes = Duration.between(startTime, visitTime).toMinutes();
        if (diffMinutes < 0 || diffMinutes % slotIntervalMinutes != 0) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_SLOT_TIME);
        }
    }

    private void validateDuplicateMyReservation(
            Long accountId, Store store, LocalDate visitDate, LocalTime visitTime
    ) {
        boolean exists = visitReservationRepository
                .existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                        accountId, store.getStoreId(), visitDate, visitTime,
                        List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED));
        if (exists) {
            throw new BusinessException(ErrorCode.VISIT_RESERVATION_ALREADY_EXISTS);
        }
    }

    private void validateBusinessHours(Store store, LocalDate visitDate, LocalTime visitTime, LocalTime endTime) {
        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(visitDate.getDayOfWeek());
        StoreBusinessHour businessHour = storeBusinessHourRepository
                .findByStore_StoreIdAndDayOfWeek(store.getStoreId(), dayOfWeek)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_STORE_BUSINESS_HOURS_NOT_SET));
        if (businessHour.isClosed()) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_CLOSED_DAY);
        }
        if (businessHour.getOpenTime() == null || businessHour.getCloseTime() == null) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_BUSINESS_HOURS_NOT_SET);
        }
        if (visitTime.isBefore(businessHour.getOpenTime()) || !visitTime.isBefore(businessHour.getCloseTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_OUTSIDE_BUSINESS_HOURS);
        }
        if (endTime.isAfter(businessHour.getCloseTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_OUTSIDE_BUSINESS_HOURS);
        }
    }

    private int resolvePartySize(Integer partySize) {
        if (partySize == null) {
            return 1;
        }
        if (partySize < 1) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_PARTY_SIZE);
        }
        return partySize;
    }

    private List<LocalTime> generateSlotTimes(StoreVisitReservationSetting setting) {
        List<LocalTime> times = new ArrayList<>();
        LocalTime current = setting.getStartTime();
        LocalTime end = setting.getEndTime();
        int interval = setting.getSlotIntervalMinutes();
        while (!current.plusMinutes(interval).isAfter(end)) {
            times.add(current);
            current = current.plusMinutes(interval);
        }
        return times;
    }
}
