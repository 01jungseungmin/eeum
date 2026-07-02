package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationSettingUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotItemRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationTimeSlotUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationSettingResponseDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationTimeSlotResponseDto;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.entity.VisitReservationTimeSlot;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitReservationSettingServiceTest {

    @InjectMocks private VisitReservationSettingService visitReservationSettingService;

    @Mock private StoreRepository storeRepository;
    @Mock private StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    @Mock private VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
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

    private StoreVisitReservationSetting createEnabledSetting(Store store) {
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(18, 0));
        return setting;
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

    // ──────────────── getSetting ────────────────

    @Test
    void 예약_설정_조회_정상동작() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = createEnabledSetting(store);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        // when
        VisitReservationSettingResponseDto result = visitReservationSettingService.getSetting(ownerAccountId);

        // then
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getSlotIntervalMinutes()).isEqualTo(30);
        assertThat(result.getCancelDeadlineMinutes()).isEqualTo(30);
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void 예약_설정_조회_시_상점이_없으면_STORE_NOT_FOUND() {
        when(storeRepository.findByAccount_AccountId(eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationSettingService.getSetting(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 예약_설정_조회_시_설정이_없으면_RESERVATION_SETTING_NOT_FOUND() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationSettingService.getSetting(ownerAccountId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_SETTING_NOT_FOUND);
    }

    // ──────────────── updateSetting ────────────────

    @Test
    void 예약_설정_수정_시_락_획득_실패면_LOCK_RESERVATION_FAILED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(redisLockService.executeWithLock(anyString(), any(), any(ErrorCode.class), any(Supplier.class)))
                .thenThrow(new BusinessException(ErrorCode.LOCK_RESERVATION_FAILED));

        assertThatThrownBy(() -> visitReservationSettingService.updateSetting(ownerAccountId,
                mock(VisitReservationSettingUpdateRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_RESERVATION_FAILED);
    }

    @Test
    void 예약_설정_수정_정상동작() {
        // given
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);

        VisitReservationSettingUpdateRequestDto request = new VisitReservationSettingUpdateRequestDto();
        ReflectionTestUtils.setField(request, "enabled", true);
        ReflectionTestUtils.setField(request, "slotIntervalMinutes", 60);
        ReflectionTestUtils.setField(request, "sameDayReservationAllowed", false);
        ReflectionTestUtils.setField(request, "cancelDeadlineMinutes", 60);
        ReflectionTestUtils.setField(request, "startTime", LocalTime.of(10, 0));
        ReflectionTestUtils.setField(request, "endTime", LocalTime.of(20, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        // when
        VisitReservationSettingResponseDto result = visitReservationSettingService.updateSetting(ownerAccountId, request);

        // then
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getSlotIntervalMinutes()).isEqualTo(60);
        assertThat(result.getSameDayReservationAllowed()).isFalse();
        assertThat(result.getCancelDeadlineMinutes()).isEqualTo(60);
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(20, 0));
    }

    // ──────────────── getTimeSlots ────────────────

    @Test
    void 시간대_설정_조회_정상동작_오버라이드_없음() {
        // given: startTime=09:00, endTime=10:00, interval=30 → 슬롯 2개(09:00, 09:30)
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(10, 0));
        LocalDate date = LocalDate.now().plusDays(3);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(eq(10L), eq(date)))
                .thenReturn(List.of());

        // when
        List<VisitReservationTimeSlotResponseDto> result = visitReservationSettingService.getTimeSlots(ownerAccountId, date);

        // then: 09:00, 09:30 두 슬롯 반환, 오버라이드 없으므로 enabled=true
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(1).getTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(result.get(0).getEnabled()).isTrue();
        assertThat(result.get(0).getClosed()).isFalse();
    }

    @Test
    void 시간대_설정_조회_비활성화_오버라이드_슬롯_적용() {
        // given: 09:00 슬롯을 disabled 오버라이드 → enabled=false, closed=true
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(9, 30));
        LocalDate date = LocalDate.now().plusDays(3);

        VisitReservationTimeSlot overrideSlot = VisitReservationTimeSlot.create(
                store, date, LocalTime.of(9, 0), false);
        ReflectionTestUtils.setField(overrideSlot, "timeSlotId", 100L);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(eq(10L), eq(date)))
                .thenReturn(List.of(overrideSlot));

        // when
        List<VisitReservationTimeSlotResponseDto> result = visitReservationSettingService.getTimeSlots(ownerAccountId, date);

        // then: 오버라이드 값 반영 (disabled → closed=true)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimeSlotId()).isEqualTo(100L);
        assertThat(result.get(0).getEnabled()).isFalse();
        assertThat(result.get(0).getClosed()).isTrue();
    }

    @Test
    void 시간대_설정_조회_시_상점이_없으면_STORE_NOT_FOUND() {
        when(storeRepository.findByAccount_AccountId(eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationSettingService.getTimeSlots(1L, LocalDate.now().plusDays(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    // ──────────────── updateTimeSlots ────────────────

    @Test
    void 시간대_수정_시_락_획득_실패면_LOCK_RESERVATION_FAILED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(redisLockService.executeWithLock(anyString(), any(), any(ErrorCode.class), any(Supplier.class)))
                .thenThrow(new BusinessException(ErrorCode.LOCK_RESERVATION_FAILED));

        assertThatThrownBy(() -> visitReservationSettingService.updateTimeSlots(ownerAccountId,
                mock(VisitReservationTimeSlotUpdateRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_RESERVATION_FAILED);
    }

    @Test
    void 시간대_수정_enabled_true이면_기존_오버라이드_삭제() {
        // given: enabled=true → 기존 오버라이드 삭제, 신규 저장 없음
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(9, 30));
        LocalDate date = LocalDate.now().plusDays(3);

        VisitReservationTimeSlot existingOverride = VisitReservationTimeSlot.create(
                store, date, LocalTime.of(9, 0), false);

        VisitReservationTimeSlotItemRequestDto slotItem = new VisitReservationTimeSlotItemRequestDto();
        ReflectionTestUtils.setField(slotItem, "time", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(slotItem, "enabled", true); // enabled=true → delete override

        VisitReservationTimeSlotUpdateRequestDto request = new VisitReservationTimeSlotUpdateRequestDto();
        ReflectionTestUtils.setField(request, "date", date);
        ReflectionTestUtils.setField(request, "slots", List.of(slotItem));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(eq(10L), eq(date), eq(LocalTime.of(9, 0))))
                .thenReturn(Optional.of(existingOverride));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(eq(10L), eq(date)))
                .thenReturn(List.of());

        // when
        visitReservationSettingService.updateTimeSlots(ownerAccountId, request);

        // then: 기존 오버라이드 삭제, 신규 저장 없음
        verify(visitReservationTimeSlotRepository).delete(existingOverride);
        verify(visitReservationTimeSlotRepository, never()).save(any());
    }

    @Test
    void 시간대_수정_enabled_false이면_오버라이드_저장() {
        // given: enabled=false → 활성 예약 없는 경우 오버라이드 저장
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(9, 30));
        LocalDate date = LocalDate.now().plusDays(3);

        VisitReservationTimeSlotItemRequestDto slotItem = new VisitReservationTimeSlotItemRequestDto();
        ReflectionTestUtils.setField(slotItem, "time", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(slotItem, "enabled", false); // enabled=false → save override

        VisitReservationTimeSlotUpdateRequestDto request = new VisitReservationTimeSlotUpdateRequestDto();
        ReflectionTestUtils.setField(request, "date", date);
        ReflectionTestUtils.setField(request, "slots", List.of(slotItem));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(eq(10L), eq(date), eq(LocalTime.of(9, 0))))
                .thenReturn(Optional.empty());
        when(visitReservationRepository.findOccupiedTableIds(eq(10L), any(), any(), any()))
                .thenReturn(List.of()); // 활성 예약 없음
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateOrderBySlotTimeAsc(eq(10L), eq(date)))
                .thenReturn(List.of());

        // when
        visitReservationSettingService.updateTimeSlots(ownerAccountId, request);

        // then: 신규 오버라이드 저장
        verify(visitReservationTimeSlotRepository).save(any(VisitReservationTimeSlot.class));
        verify(visitReservationTimeSlotRepository, never()).delete(any());
    }

    @Test
    void 시간대_수정_enabled_false인데_활성예약_있으면_RESERVATION_SLOT_CHANGE_NOT_ALLOWED() {
        setupPassthroughLockAndTransaction();
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId);
        Store store = createStore(10L, owner);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(9, 30));
        LocalDate date = LocalDate.now().plusDays(3);

        VisitReservationTimeSlotItemRequestDto slotItem = new VisitReservationTimeSlotItemRequestDto();
        ReflectionTestUtils.setField(slotItem, "time", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(slotItem, "enabled", false);

        VisitReservationTimeSlotUpdateRequestDto request = new VisitReservationTimeSlotUpdateRequestDto();
        ReflectionTestUtils.setField(request, "date", date);
        ReflectionTestUtils.setField(request, "slots", List.of(slotItem));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(eq(10L), eq(date), eq(LocalTime.of(9, 0))))
                .thenReturn(Optional.empty());
        when(visitReservationRepository.findOccupiedTableIds(eq(10L), any(), any(), any()))
                .thenReturn(List.of(1L)); // 활성 예약 있음

        assertThatThrownBy(() -> visitReservationSettingService.updateTimeSlots(ownerAccountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_SLOT_CHANGE_NOT_ALLOWED);
    }
}
