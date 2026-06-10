package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationStatusUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationLeftTimeSlotResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.mapper.VisitReservationMapper;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.event.ReservationApprovedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationRejectedEvent;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotCountProjection;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReservationService {

    private final VisitReservationRepository visitReservationRepository;
    private final StoreRepository storeRepository;
    private final AccountRepository accountRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;
    private final VisitReservationMapper visitReservationMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ===================== 사용자 예약 =====================
    @Transactional
    public VisitReservationResponseDto createReservation(
            Long accountId,
            Long storeId,
            VisitReservationCreateRequestDto request
    ) {

        String lockKey = createReservationLockKey(
                storeId,
                request.getVisitDate(),
                request.getVisitTime()
        );

        return redisLockService.executeWithLock(
                lockKey,
                Duration.ofSeconds(5),
                () -> transactionTemplate.execute(status ->
                        createReservationInternal(accountId, storeId, request)
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<VisitReservationResponseDto> getMyReservations(
            Long accountId,
            Pageable pageable
    ) {
        return visitReservationRepository
                .findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(accountId, pageable)
                .map(visitReservationMapper::toVisitReservationResponseDto);
    }

    @Transactional(readOnly = true)
    public VisitReservationResponseDto getMyReservationDetail(
            Long accountId,
            Long reservationId
    ) {
        VisitReservation reservation = getReservation(reservationId);

        if (!reservation.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }

        return visitReservationMapper.toVisitReservationResponseDto(reservation);
    }

    @Transactional
    public void cancelMyReservation(Long accountId, Long reservationId) {
        VisitReservation reservation = getReservation(reservationId);

        if (!reservation.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }

        if (reservation.getStatus() == VisitReservationStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        try {
            reservation.cancel();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT);
        }

        log.info("방문 예약 취소: accountId={}, reservationId={}", accountId, reservationId);
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
                .findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(
                        store.getStoreId(),
                        status,
                        pageable
                )
                .map(visitReservationMapper::toVisitReservationResponseDto);
    }

    @Transactional(readOnly = true)
    public VisitReservationResponseDto getOwnerReservationDetail(
            Long ownerAccountId,
            Long reservationId
    ) {
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
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        try {
            reservation.approve();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT);
        }

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
    public void rejectReservation(
            Long ownerAccountId,
            Long reservationId,
            VisitReservationStatusUpdateRequestDto request
    ) {
        Store store = getOwnerStore(ownerAccountId);
        VisitReservation reservation = getReservation(reservationId);

        validateStoreOwner(store, reservation);

        if (reservation.getStatus() != VisitReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        try {
            reservation.reject(request.getRejectReason());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT);
        }

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
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        try {
            reservation.complete();
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT);
        }

        log.info("방문 예약 완료: ownerAccountId={}, reservationId={}", ownerAccountId, reservationId);
    }

    //사장용 특정 날짜의 시간대별 잔여 예약 현황 조회
    // 1. 설정의 startTime/endTime/interval 로 슬롯 시각 목록 동적 생성 (DB 불필요)
    // 2. DB 오버라이드 슬롯 배치 조회 (1쿼리), PENDING·APPROVED 예약 집계 배치 조회 (1쿼리) → N+1 없이 최대 2쿼리로 처리
    // closed = !enabled OR 잔여팀 ≤ 0 OR 잔여인원 ≤ 0
    @Transactional(readOnly = true)
    public List<VisitReservationLeftTimeSlotResponseDto> getOwnerLeftTimeSlot(
            Long ownerAccountId,
            LocalDate date
    ) {
        Store store = getOwnerStore(ownerAccountId);

        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));

        // ① 슬롯 시각 동적 생성
        List<LocalTime> slotTimes = generateSlotTimes(setting);

        // ② DB 오버라이드 슬롯 조회 (1쿼리)
        Map<LocalTime, VisitReservationTimeSlot> overrideMap =
                visitReservationTimeSlotRepository
                        .findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(store.getStoreId(), date)
                        .stream()
                        .collect(Collectors.toMap(VisitReservationTimeSlot::getSlotTime, s -> s));

        // ③ 예약 집계 배치 조회 (1쿼리)
        Map<LocalTime, VisitReservationTimeSlotCountProjection> countMap =
                visitReservationRepository.countTimeSlotsByStoreAndDate(
                                store.getStoreId(), date,
                                List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED))
                        .stream()
                        .collect(Collectors.toMap(
                                VisitReservationTimeSlotCountProjection::getReservationTime, p -> p));

        return slotTimes.stream()
                .map(time -> {
                    VisitReservationTimeSlot override = overrideMap.get(time);
                    VisitReservationTimeSlotCountProjection count = countMap.get(time);
                    int reservedTeams  = count == null ? 0 : count.getReservedTeams().intValue();
                    int reservedPeople = count == null ? 0 : count.getReservedPeople().intValue();

                    if (override != null) {
                        // 오버라이드 슬롯 — DB에 저장된 설정값 사용
                        return VisitReservationLeftTimeSlotResponseDto.of(
                                override.getTimeSlotId(), time,
                                override.getMaxTeamCount(), reservedTeams,
                                override.getMaxVisitorCount(), reservedPeople,
                                override.isEnabled());
                    }
                    // 기본 슬롯 — setting 기본값 사용, timeSlotId = null
                    return VisitReservationLeftTimeSlotResponseDto.of(
                            null, time,
                            setting.getDefaultMaxTeamCount(), reservedTeams,
                            setting.getDefaultMaxVisitorCount(), reservedPeople,
                            true);
                })
                .toList();
    }

    // ===================== 내부 유틸 =====================

    private VisitReservation getReservation(Long reservationId) {
        return visitReservationRepository.findById(reservationId)
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

    private void validateDuplicateMyReservation(
            Long accountId,
            Store store,
            LocalDate visitDate,
            LocalTime visitTime
    ) {
        boolean exists = visitReservationRepository
                .existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                        accountId,
                        store.getStoreId(),
                        visitDate,
                        visitTime,
                        List.of(
                                VisitReservationStatus.PENDING,
                                VisitReservationStatus.APPROVED
                        )
                );

        if (exists) {
            throw new BusinessException(ErrorCode.VISIT_RESERVATION_ALREADY_EXISTS);
        }
    }

    private void validateVisitDateTime(LocalDate visitDate, LocalTime visitTime) {
        if (visitDate == null || visitTime == null) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        if (visitDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        if (visitDate.isEqual(LocalDate.now()) && visitTime.isBefore(LocalTime.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        if (visitTime.getMinute() % 30 != 0) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }
    }

    private void validateBusinessHours(
            Store store,
            LocalDate visitDate,
            LocalTime visitTime
    ) {
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

        boolean afterOrEqualOpenTime = !visitTime.isBefore(businessHour.getOpenTime());
        boolean beforeCloseTime = visitTime.isBefore(businessHour.getCloseTime());

        if (!afterOrEqualOpenTime || !beforeCloseTime) {
            throw new BusinessException(ErrorCode.RESERVATION_STORE_BUSINESS_HOURS_NOT_SET);
        }
    }

    private void validateTimeSlotAvailability(
            Store store,
            LocalDate visitDate,
            LocalTime visitTime,
            Integer visitorCount
    ) {
        StoreVisitReservationSetting setting = storeVisitReservationSettingRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SETTING_NOT_FOUND));

        if (!setting.isEnabled()) {
            throw new BusinessException(ErrorCode.RESERVATION_DISABLED);
        }

        VisitReservationTimeSlot slot = visitReservationTimeSlotRepository
                .findByStore_StoreIdAndSlotDateAndSlotTime(
                        store.getStoreId(),
                        visitDate,
                        visitTime
                )
                .orElseGet(() -> VisitReservationTimeSlot.create(
                        store,
                        visitDate,
                        visitTime,
                        setting.getDefaultMaxVisitorCount(),
                        setting.getDefaultMaxTeamCount(),
                        true  // 명시적 슬롯 설정 없으면 기본 활성화 (isEnabled는 위에서 이미 검사)
                ));

        if (!slot.isEnabled()) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
        }

        List<VisitReservationStatus> activeStatuses = List.of(
                VisitReservationStatus.PENDING,
                VisitReservationStatus.APPROVED
        );

        Integer reservedVisitorCount = visitReservationRepository.sumVisitorCountByTimeSlot(
                store.getStoreId(),
                visitDate,
                visitTime,
                activeStatuses
        );

        long reservedTeamCount = visitReservationRepository
                .countByStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                        store.getStoreId(),
                        visitDate,
                        visitTime,
                        activeStatuses
                );

        int requestVisitorCount = visitorCount != null ? visitorCount : 1;

        if (reservedVisitorCount + requestVisitorCount > slot.getMaxVisitorCount()) {
            throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
        }

        if (reservedTeamCount + 1 > slot.getMaxTeamCount()) {
            throw new BusinessException(ErrorCode.RESERVATION_TEAM_LIMIT_EXCEEDED);
        }
    }

    private String createReservationLockKey(
            Long storeId,
            LocalDate visitDate,
            LocalTime visitTime
    ) {
        return "lock:reservation:visit:store:"
                + storeId
                + ":date:"
                + visitDate
                + ":time:"
                + visitTime;
    }

    private VisitReservationResponseDto createReservationInternal(
            Long accountId,
            Long storeId,
            VisitReservationCreateRequestDto request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        validateReservableStore(store);
        validateVisitDateTime(request.getVisitDate(), request.getVisitTime());
        validateBusinessHours(store, request.getVisitDate(), request.getVisitTime());
        validateDuplicateMyReservation(accountId,store,request.getVisitDate(),request.getVisitTime());
        validateTimeSlotAvailability(store, request.getVisitDate(),request.getVisitTime(), request.getVisitorCount());

        VisitReservation reservation = VisitReservation.create(
                store,
                account,
                request.getVisitDate(),
                request.getVisitTime(),
                request.getVisitorCount(),
                request.getRequestMessage()
        );

        visitReservationRepository.save(reservation);

        log.info("방문 예약 생성: accountId={}, storeId={}, reservationId={}",
                accountId, storeId, reservation.getVisitReservationId());

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

    //설정(startTime, endTime, slotIntervalMinutes)에서 슬롯 시각 목록을 동적으로 생성 - endTime 미만의 시각까지만 생성

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


}