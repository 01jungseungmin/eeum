package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.StoreTableConfigRequestDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    public List<StoreTableResponseDto> getTables(Long ownerAccountId) {
        Store store = getOwnerStore(ownerAccountId);
        return storeTableRepository.findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(store.getStoreId())
                .stream()
                .map(StoreTableResponseDto::from)
                .toList();
    }

    public List<StoreTableResponseDto> configureTables(Long ownerAccountId, StoreTableConfigRequestDto request) {
        Store store = getOwnerStore(ownerAccountId);
        Long storeId = store.getStoreId();

        return redisLockService.executeWithLock(
                LockKeys.storeReservation(storeId),
                Duration.ofSeconds(30),
                ErrorCode.COMMON_CONFLICT,
                () -> transactionTemplate.execute(status -> configureTablesInternal(store, storeId, request))
        );
    }

    private List<StoreTableResponseDto> configureTablesInternal(
            Store store, Long storeId, StoreTableConfigRequestDto request
    ) {
        if (visitReservationRepository.existsActiveFutureReservations(
                storeId, ACTIVE_STATUSES, LocalDateTime.now(), LocalDate.now())) {
            throw new BusinessException(ErrorCode.RESERVATION_TABLE_CHANGE_NOT_ALLOWED);
        }

        storeTableRepository.findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId)
                .forEach(StoreTable::deactivate);

        List<StoreTable> newTables = new ArrayList<>();
        for (StoreTableConfigRequestDto.TableItem item : request.getTables()) {
            for (int i = 1; i <= item.getCount(); i++) {
                String name = item.getCapacity() + "인석-" + i;
                newTables.add(StoreTable.create(store, item.getCapacity(), name));
            }
        }
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
