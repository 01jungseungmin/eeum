package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.StoreTableConfigRequestDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableListResponseDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableResponseDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableSummaryResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.repository.CapacityCountProjection;
import com.eeum.eeum.domain.reservation.repository.StoreTableRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreTableService {

    private final StoreRepository storeRepository;
    private final StoreTableRepository storeTableRepository;
    private final VisitReservationRepository visitReservationRepository;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;

    private static final List<VisitReservationStatus> ACTIVE_STATUSES =
            List.of(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED);

    @Transactional(readOnly = true)
    public StoreTableListResponseDto getTables(Long ownerAccountId) {
        Store store = getOwnerStore(ownerAccountId);

        List<StoreTableResponseDto> tables = storeTableRepository
                .findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(
                        store.getStoreId()
                )
                .stream()
                .map(StoreTableResponseDto::from)
                .toList();

        List<CapacityCountProjection> aggregates =
                storeTableRepository.countActiveTablesByCapacity(store.getStoreId());

        StoreTableSummaryResponseDto summary =
                StoreTableSummaryResponseDto.from(aggregates);

        return StoreTableListResponseDto.of(tables, summary);
    }

    // 활성 테이블 기준 수용 인원별 개수 + 총 개수 (비활성/삭제 테이블 제외) — DB 집계 쿼리 사용
    @Transactional(readOnly = true)
    public StoreTableSummaryResponseDto getTableSummary(Long ownerAccountId) {
        Store store = getOwnerStore(ownerAccountId);
        return StoreTableSummaryResponseDto.from(
                storeTableRepository.countActiveTablesByCapacity(store.getStoreId()));
    }

    public List<StoreTableResponseDto> configureTables(Long ownerAccountId, StoreTableConfigRequestDto request) {
        Store store = getOwnerStore(ownerAccountId);
        Long storeId = store.getStoreId();

        return redisLockService.executeWithLock(
                LockKeys.storeReservation(storeId),
                Duration.ofSeconds(30),
                ErrorCode.LOCK_RESERVATION_FAILED,
                () -> transactionTemplate.execute(status -> configureTablesInternal(store, storeId, request))
        );
    }

    private List<StoreTableResponseDto> configureTablesInternal(
            Store store,
            Long storeId,
            StoreTableConfigRequestDto request
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (visitReservationRepository.existsActiveFutureReservations(
                storeId, ACTIVE_STATUSES, now, now.toLocalDate())) {
            throw new BusinessException(ErrorCode.RESERVATION_TABLE_CHANGE_NOT_ALLOWED);
        }

        storeTableRepository.findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId)
                .forEach(StoreTable::deactivate);

        Map<Integer, Integer> capacityCountMap = request.getTables().stream()
                .collect(Collectors.toMap(
                        StoreTableConfigRequestDto.TableItem::getCapacity,
                        StoreTableConfigRequestDto.TableItem::getCount,
                        Integer::sum
                ));

        List<StoreTable> newTables = new ArrayList<>();
        capacityCountMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int capacity = entry.getKey();
                    int count = entry.getValue();
                    for (int i = 1; i <= count; i++) {
                        newTables.add(StoreTable.create(store, capacity, capacity + "인석-" + i));
                    }
                });

        storeTableRepository.saveAll(newTables);

        return newTables.stream()
                .map(StoreTableResponseDto::from)
                .toList();
    }

    private Store getOwnerStore(Long ownerAccountId) {
        return storeRepository.findByAccount_AccountId(ownerAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
