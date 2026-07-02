package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.StoreTableConfigRequestDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableResponseDto;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.repository.StoreTableRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreTableServiceTest {

    @InjectMocks private StoreTableService storeTableService;

    @Mock private StoreRepository storeRepository;
    @Mock private StoreTableRepository storeTableRepository;
    @Mock private VisitReservationRepository visitReservationRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private TransactionTemplate transactionTemplate;

    // ──────────────── 헬퍼 ────────────────

    private Account createAccount(Long id) {
        Account account = Account.createUser("owner@test.com", "pw", "사장", "owner", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private Store createStore(Long id, Account owner) {
        Store store = Store.createForOwnerSignup(owner, "테스트상점", "서울시", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", id);
        ReflectionTestUtils.setField(store, "status", StoreStatus.OPEN);
        return store;
    }

    private StoreTable createStoreTable(Long id, Store store, int capacity) {
        StoreTable table = StoreTable.create(store, capacity, capacity + "인석-1");
        ReflectionTestUtils.setField(table, "storeTableId", id);
        return table;
    }

    private StoreTableConfigRequestDto buildConfigRequest(int capacity, int count) {
        StoreTableConfigRequestDto.TableItem item = new StoreTableConfigRequestDto.TableItem();
        ReflectionTestUtils.setField(item, "capacity", capacity);
        ReflectionTestUtils.setField(item, "count", count);
        StoreTableConfigRequestDto request = new StoreTableConfigRequestDto();
        ReflectionTestUtils.setField(request, "tables", List.of(item));
        return request;
    }

    @SuppressWarnings("unchecked")
    private void setupPassthroughLockAndTransaction() {
        doAnswer(inv -> {
            Supplier<Object> s = (Supplier<Object>) inv.getArgument(3);
            return s.get();
        }).when(redisLockService).executeWithLock(anyString(), any(), any(ErrorCode.class), any(Supplier.class));

        doAnswer(inv -> {
            TransactionCallback<Object> cb = (TransactionCallback<Object>) inv.getArgument(0);
            return cb.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    // ──────────────── getTables ────────────────

    @Test
    void 테이블_목록_조회_정상동작() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreTable table1 = createStoreTable(1L, store, 2);
        StoreTable table2 = createStoreTable(2L, store, 4);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeTableRepository.findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(eq(10L)))
                .thenReturn(List.of(table1, table2));

        // when
        List<StoreTableResponseDto> result = storeTableService.getTables(ownerAccountId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCapacity()).isEqualTo(2);
        assertThat(result.get(1).getCapacity()).isEqualTo(4);
    }

    @Test
    void 테이블_목록_조회_시_상점이_없으면_STORE_NOT_FOUND() {
        when(storeRepository.findByAccount_AccountId(eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeTableService.getTables(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    // ──────────────── configureTables ────────────────

    @Test
    void 테이블_구성_변경_시_락_획득_실패면_COMMON_CONFLICT() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(redisLockService.executeWithLock(anyString(), any(), any(ErrorCode.class), any(Supplier.class)))
                .thenThrow(new BusinessException(ErrorCode.COMMON_CONFLICT));

        assertThatThrownBy(() -> storeTableService.configureTables(ownerAccountId, buildConfigRequest(4, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_CONFLICT);
    }

    @Test
    void 테이블_구성_변경_시_미래_활성_예약이_있으면_RESERVATION_TABLE_CHANGE_NOT_ALLOWED() {
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.existsActiveFutureReservations(eq(10L), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> storeTableService.configureTables(ownerAccountId, buildConfigRequest(4, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_TABLE_CHANGE_NOT_ALLOWED);
    }

    @Test
    void 테이블_구성_변경_정상동작() {
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreTable existingTable = createStoreTable(99L, store, 2);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.existsActiveFutureReservations(eq(10L), any(), any(), any()))
                .thenReturn(false);
        when(storeTableRepository.findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(eq(10L)))
                .thenReturn(List.of(existingTable));
        when(storeTableRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // when: 4인석 2개로 구성 변경
        List<StoreTableResponseDto> result = storeTableService.configureTables(ownerAccountId, buildConfigRequest(4, 2));

        // then
        assertThat(existingTable.isActive()).isFalse(); // 기존 테이블 비활성화
        assertThat(result).hasSize(2);
        result.forEach(dto -> assertThat(dto.getCapacity()).isEqualTo(4));
        verify(storeTableRepository).saveAll(argThat(tables -> {
            @SuppressWarnings("unchecked")
            List<StoreTable> list = (List<StoreTable>) tables;
            return list.size() == 2;
        }));
    }
}
