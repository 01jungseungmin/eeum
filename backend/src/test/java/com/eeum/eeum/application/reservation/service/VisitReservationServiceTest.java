package com.eeum.eeum.application.reservation.service;

import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationStatusUpdateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.VisitReservationResponseDto;
import com.eeum.eeum.application.reservation.mapper.VisitReservationMapper;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.event.ReservationApprovedEvent;
import com.eeum.eeum.domain.reservation.event.ReservationRejectedEvent;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitReservationServiceTest {

    @InjectMocks private VisitReservationService visitReservationService;

    @Mock private VisitReservationRepository visitReservationRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StoreBusinessHourRepository storeBusinessHourRepository;
    @Mock private StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    @Mock private VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
    @Mock private StoreTableRepository storeTableRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private VisitReservationMapper visitReservationMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    // ──────────────── 헬퍼 ────────────────

    private Account createAccount(Long id, String name, String nickname) {
        Account account = Account.createUser(nickname + "@test.com", "pw", name, nickname, "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private Store createStore(Long id, Account owner, StoreStatus status) {
        Store store = Store.createForOwnerSignup(owner, "테스트상점", "서울시", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", id);
        ReflectionTestUtils.setField(store, "status", status);
        return store;
    }

    private StoreVisitReservationSetting createEnabledSetting(Store store) {
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        // cancelDeadlineMinutes=30, startTime=09:00, endTime=18:00, interval=30, sameDayAllowed=true
        setting.update(true, 30, true, 30, LocalTime.of(9, 0), LocalTime.of(18, 0));
        return setting;
    }

    private VisitReservation createReservation(Long id, Store store, Account account,
                                                VisitReservationStatus status,
                                                LocalDate date, LocalTime time) {
        StoreTable table = StoreTable.create(store, 4, "4인석-1");
        ReflectionTestUtils.setField(table, "storeTableId", 1L);
        LocalDateTime start = LocalDateTime.of(date, time);
        VisitReservation reservation = VisitReservation.create(
                store, account, date, time, 2, null, table, start, start.plusMinutes(30));
        ReflectionTestUtils.setField(reservation, "visitReservationId", id);
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    private VisitReservationCreateRequestDto createRequest(LocalDate date, LocalTime time, Integer partySize) {
        VisitReservationCreateRequestDto req = new VisitReservationCreateRequestDto();
        ReflectionTestUtils.setField(req, "visitDate", date);
        ReflectionTestUtils.setField(req, "visitTime", time);
        ReflectionTestUtils.setField(req, "partySize", partySize);
        return req;
    }

    /** 분산 락 + 트랜잭션을 공급자가 직접 실행되도록 설정 */
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

    // ──────────────── getMyReservations ────────────────

    @Test
    void 내_예약_목록_조회_정상동작() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(1L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        PageRequest pageable = PageRequest.of(0, 10);

        when(visitReservationRepository.findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(reservation)));
        when(storeReviewRepository.findVisitReservationIdsWithReview(List.of(1L))).thenReturn(Set.of());
        when(visitReservationMapper.toVisitReservationResponseDto(reservation, false))
                .thenReturn(mock(VisitReservationResponseDto.class));

        // when
        Page<VisitReservationResponseDto> result = visitReservationService.getMyReservations(accountId, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 내_예약_목록_조회_시_리뷰가_있으면_hasReview가_true로_매핑된다() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(1L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        PageRequest pageable = PageRequest.of(0, 10);

        when(visitReservationRepository.findByAccount_AccountIdOrderByVisitDateDescVisitTimeDesc(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(reservation)));
        when(storeReviewRepository.findVisitReservationIdsWithReview(List.of(1L))).thenReturn(Set.of(1L));
        when(visitReservationMapper.toVisitReservationResponseDto(reservation, true))
                .thenReturn(mock(VisitReservationResponseDto.class));

        // when
        Page<VisitReservationResponseDto> result = visitReservationService.getMyReservations(accountId, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(visitReservationMapper).toVisitReservationResponseDto(reservation, true);
    }

    // ──────────────── getMyReservationDetail ────────────────

    @Test
    void 내_예약_상세_조회_정상동작() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        VisitReservationResponseDto dto = mock(VisitReservationResponseDto.class);

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));
        when(storeReviewRepository.existsByVisitReservation_VisitReservationId(10L)).thenReturn(false);
        when(visitReservationMapper.toVisitReservationResponseDto(reservation, false)).thenReturn(dto);

        // when
        VisitReservationResponseDto result = visitReservationService.getMyReservationDetail(accountId, 10L);

        // then
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void 내_예약_상세_조회_시_리뷰가_있으면_hasReview_true로_매핑된다() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        VisitReservationResponseDto dto = mock(VisitReservationResponseDto.class);

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));
        when(storeReviewRepository.existsByVisitReservation_VisitReservationId(10L)).thenReturn(true);
        when(visitReservationMapper.toVisitReservationResponseDto(reservation, true)).thenReturn(dto);

        // when
        VisitReservationResponseDto result = visitReservationService.getMyReservationDetail(accountId, 10L);

        // then
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void 내_예약_상세_조회_시_예약이_없으면_RESERVATION_NOT_FOUND() {
        when(visitReservationRepository.findByVisitReservationId(eq(999L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.getMyReservationDetail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void 내_예약_상세_조회_시_다른_사람_예약이면_RESERVATION_ACCESS_DENIED() {
        // given
        Long requesterId = 1L;
        Account owner = createAccount(2L, "타인", "other");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, owner,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.getMyReservationDetail(requesterId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_ACCESS_DENIED);
    }

    // ──────────────── cancelMyReservation ────────────────

    @Test
    void 내_예약_취소_정상동작() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.APPROVED, LocalDate.now().plusDays(7), LocalTime.of(12, 0));
        StoreVisitReservationSetting setting = createEnabledSetting(store); // cancelDeadlineMinutes=30

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        // when
        visitReservationService.cancelMyReservation(accountId, 10L);

        // then
        assertThat(reservation.getStatus()).isEqualTo(VisitReservationStatus.CANCELED);
    }

    @Test
    void 내_예약_취소_시_다른_사람_예약이면_RESERVATION_ACCESS_DENIED() {
        Account owner = createAccount(2L, "타인", "other");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, owner,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.cancelMyReservation(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_ACCESS_DENIED);
    }

    @Test
    void 내_예약_취소_시_CANCELED_상태면_RESERVATION_CANCEL_NOT_ALLOWED() {
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.CANCELED, LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.cancelMyReservation(accountId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
    }

    @Test
    void 내_예약_취소_시_예약설정이_없으면_RESERVATION_SETTING_NOT_FOUND() {
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.cancelMyReservation(accountId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_SETTING_NOT_FOUND);
    }

    @Test
    void 내_예약_취소_시_취소기한_초과면_RESERVATION_CANCEL_NOT_ALLOWED() {
        // given: visitDate=내일 00:01, cancelDeadlineMinutes=2000(약 33시간) → 이미 기한 초과
        Long accountId = 1L;
        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(10L, store, account,
                VisitReservationStatus.PENDING, LocalDate.now().plusDays(1), LocalTime.of(0, 1));

        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 2000, LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(visitReservationRepository.findByVisitReservationId(eq(10L))).thenReturn(Optional.of(reservation));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> visitReservationService.cancelMyReservation(accountId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
    }

    // ──────────────── createReservation (락 + 내부 로직) ────────────────

    @Test
    void 예약_생성_시_락_획득_실패면_LOCK_RESERVATION_FAILED() {
        when(redisLockService.executeWithLock(anyString(), any(), any(ErrorCode.class), any(Supplier.class)))
                .thenThrow(new BusinessException(ErrorCode.LOCK_RESERVATION_FAILED));

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, mock(VisitReservationCreateRequestDto.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_RESERVATION_FAILED);
    }

    @Test
    void 예약_생성_내부_계정이_없으면_ACCOUNT_NOT_FOUND() {
        setupPassthroughLockAndTransaction();
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(10, 0), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 예약_생성_내부_상점이_없으면_STORE_NOT_FOUND() {
        setupPassthroughLockAndTransaction();
        Account account = createAccount(1L, "사용자", "user");
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(10, 0), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 예약_생성_내부_상점이_OPEN이_아니면_RESERVATION_STORE_NOT_RESERVABLE() {
        setupPassthroughLockAndTransaction();
        Account account = createAccount(1L, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.TEMP_CLOSED);
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(10, 0), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_STORE_NOT_RESERVABLE);
    }

    @Test
    void 예약_생성_내부_예약설정_없으면_RESERVATION_SETTING_NOT_FOUND() {
        setupPassthroughLockAndTransaction();
        Account account = createAccount(1L, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(10, 0), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_SETTING_NOT_FOUND);
    }

    @Test
    void 예약_생성_내부_예약기능_비활성화면_RESERVATION_DISABLED() {
        setupPassthroughLockAndTransaction();
        Account account = createAccount(1L, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        // createDefault → enabled=false
        StoreVisitReservationSetting disabledSetting = StoreVisitReservationSetting.createDefault(store);
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(disabledSetting));

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(10, 0), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_DISABLED);
    }

    @Test
    void 예약_생성_내부_슬롯_간격이_맞지_않으면_RESERVATION_INVALID_SLOT_TIME() {
        // given: startTime=09:00, interval=30, visitTime=09:15 → diff=15, 15%30≠0
        setupPassthroughLockAndTransaction();
        Account account = createAccount(1L, "사용자", "user");
        Store store = createStore(10L, account, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        when(accountRepository.findById(eq(1L))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> visitReservationService.createReservation(
                1L, 10L, createRequest(LocalDate.now().plusDays(7), LocalTime.of(9, 15), 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_INVALID_SLOT_TIME);
    }

    @Test
    void 예약_생성_내부_중복예약이면_VISIT_RESERVATION_ALREADY_EXISTS() {
        setupPassthroughLockAndTransaction();
        Long accountId = 1L;
        Long storeId = 10L;
        LocalDate visitDate = LocalDate.now().plusDays(7);
        LocalTime visitTime = LocalTime.of(10, 0); // 09:00~18:00, 30분 간격 → 유효
        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(visitDate.getDayOfWeek());

        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(storeId, account, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        StoreBusinessHour businessHour = StoreBusinessHour.create(store, dayOfWeek, false,
                LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(storeId))).thenReturn(Optional.of(setting));
        when(storeBusinessHourRepository.findByStore_StoreIdAndDayOfWeek(eq(storeId), eq(dayOfWeek)))
                .thenReturn(Optional.of(businessHour));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(eq(storeId), eq(visitDate), eq(visitTime)))
                .thenReturn(Optional.empty());
        when(visitReservationRepository.existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                eq(accountId), eq(storeId), eq(visitDate), eq(visitTime), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> visitReservationService.createReservation(
                accountId, storeId, createRequest(visitDate, visitTime, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VISIT_RESERVATION_ALREADY_EXISTS);
    }

    @Test
    void 예약_생성_내부_가용테이블_없으면_RESERVATION_TABLE_UNAVAILABLE() {
        setupPassthroughLockAndTransaction();
        Long accountId = 1L;
        Long storeId = 10L;
        LocalDate visitDate = LocalDate.now().plusDays(7);
        LocalTime visitTime = LocalTime.of(10, 0);
        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(visitDate.getDayOfWeek());

        Account account = createAccount(accountId, "사용자", "user");
        Store store = createStore(storeId, account, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        StoreBusinessHour businessHour = StoreBusinessHour.create(store, dayOfWeek, false,
                LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account));
        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(storeId))).thenReturn(Optional.of(setting));
        when(storeBusinessHourRepository.findByStore_StoreIdAndDayOfWeek(eq(storeId), eq(dayOfWeek)))
                .thenReturn(Optional.of(businessHour));
        when(visitReservationTimeSlotRepository.findByStore_StoreIdAndSlotDateAndSlotTime(eq(storeId), eq(visitDate), eq(visitTime)))
                .thenReturn(Optional.empty());
        when(visitReservationRepository.existsByAccount_AccountIdAndStore_StoreIdAndVisitDateAndVisitTimeAndStatusIn(
                eq(accountId), eq(storeId), eq(visitDate), eq(visitTime), any()))
                .thenReturn(false);
        when(visitReservationRepository.findOccupiedTableIds(eq(storeId), any(), any(), any()))
                .thenReturn(List.of(1L, 2L));
        when(storeTableRepository.findAvailableTablesExcludingOccupied(eq(storeId), anyInt(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> visitReservationService.createReservation(
                accountId, storeId, createRequest(visitDate, visitTime, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_TABLE_UNAVAILABLE);
    }

    // ──────────────── getOwnerReservations ────────────────

    @Test
    void 사장_예약_목록_조회_상태필터_없음() {
        Long ownerAccountId = 1L;
        Long storeId = 10L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(storeId, owner, StoreStatus.OPEN);
        PageRequest pageable = PageRequest.of(0, 10);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByStore_StoreIdOrderByVisitDateDescVisitTimeDesc(eq(storeId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<VisitReservationResponseDto> result = visitReservationService.getOwnerReservations(ownerAccountId, null, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(visitReservationRepository, never())
                .findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(any(), any(), any());
    }

    @Test
    void 사장_예약_목록_조회_PENDING_필터() {
        Long ownerAccountId = 1L;
        Long storeId = 10L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(storeId, owner, StoreStatus.OPEN);
        PageRequest pageable = PageRequest.of(0, 10);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(
                eq(storeId), eq(VisitReservationStatus.PENDING), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<VisitReservationResponseDto> result = visitReservationService.getOwnerReservations(
                ownerAccountId, VisitReservationStatus.PENDING, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(visitReservationRepository)
                .findByStore_StoreIdAndStatusOrderByVisitDateDescVisitTimeDesc(
                        eq(storeId), eq(VisitReservationStatus.PENDING), eq(pageable));
    }

    // ──────────────── getOwnerReservationDetail ────────────────

    @Test
    void 사장_예약_상세_조회_정상동작() {
        Long ownerAccountId = 1L;
        Long storeId = 10L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(storeId, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.PENDING,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        VisitReservationResponseDto dto = mock(VisitReservationResponseDto.class);

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));
        when(visitReservationMapper.toVisitReservationResponseDto(reservation)).thenReturn(dto);

        VisitReservationResponseDto result = visitReservationService.getOwnerReservationDetail(ownerAccountId, 100L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void 사장_예약_상세_조회_시_다른_상점_예약이면_RESERVATION_ACCESS_DENIED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store myStore = createStore(10L, owner, StoreStatus.OPEN);
        Account otherOwner = createAccount(2L, "타사장", "owner2");
        Store otherStore = createStore(20L, otherOwner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, otherStore,
                createAccount(3L, "고객", "customer"), VisitReservationStatus.PENDING,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(myStore));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.getOwnerReservationDetail(ownerAccountId, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_ACCESS_DENIED);
    }

    // ──────────────── approveReservation ────────────────

    @Test
    void 사장_예약_승인_정상동작_및_이벤트_발행() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.PENDING,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        // when
        visitReservationService.approveReservation(ownerAccountId, 100L);

        // then
        assertThat(reservation.getStatus()).isEqualTo(VisitReservationStatus.APPROVED);
        verify(eventPublisher).publishEvent(any(ReservationApprovedEvent.class));
    }

    @Test
    void 사장_예약_승인_시_PENDING이_아니면_RESERVATION_APPROVE_NOT_ALLOWED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.APPROVED,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.approveReservation(ownerAccountId, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_APPROVE_NOT_ALLOWED);
    }

    // ──────────────── rejectReservation ────────────────

    @Test
    void 사장_예약_거절_정상동작_및_이벤트_발행() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.PENDING,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));
        VisitReservationStatusUpdateRequestDto request = new VisitReservationStatusUpdateRequestDto();
        ReflectionTestUtils.setField(request, "rejectReason", "해당 시간 예약 불가");

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        // when
        visitReservationService.rejectReservation(ownerAccountId, 100L, request);

        // then
        assertThat(reservation.getStatus()).isEqualTo(VisitReservationStatus.REJECTED);
        assertThat(reservation.getRejectReason()).isEqualTo("해당 시간 예약 불가");
        verify(eventPublisher).publishEvent(any(ReservationRejectedEvent.class));
    }

    @Test
    void 사장_예약_거절_시_PENDING이_아니면_RESERVATION_REJECT_NOT_ALLOWED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.APPROVED,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.rejectReservation(ownerAccountId, 100L,
                new VisitReservationStatusUpdateRequestDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_REJECT_NOT_ALLOWED);
    }

    // ──────────────── completeReservation ────────────────

    @Test
    void 사장_예약_완료_정상동작() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.APPROVED,
                LocalDate.now().plusDays(1), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        visitReservationService.completeReservation(ownerAccountId, 100L);

        assertThat(reservation.getStatus()).isEqualTo(VisitReservationStatus.COMPLETED);
    }

    @Test
    void 사장_예약_완료_시_APPROVED가_아니면_RESERVATION_COMPLETE_NOT_ALLOWED() {
        Long ownerAccountId = 1L;
        Account owner = createAccount(ownerAccountId, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        VisitReservation reservation = createReservation(100L, store,
                createAccount(2L, "고객", "customer"), VisitReservationStatus.PENDING,
                LocalDate.now().plusDays(1), LocalTime.of(10, 0));

        when(storeRepository.findByAccount_AccountId(eq(ownerAccountId))).thenReturn(Optional.of(store));
        when(visitReservationRepository.findByVisitReservationId(eq(100L))).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> visitReservationService.completeReservation(ownerAccountId, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_COMPLETE_NOT_ALLOWED);
    }

    // ──────────────── getAvailableTimeSlots (초기 검증) ────────────────

    @Test
    void 가용_슬롯_조회_시_상점이_없으면_STORE_NOT_FOUND() {
        when(storeRepository.findById(eq(999L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(999L, LocalDate.now().plusDays(1), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void 가용_슬롯_조회_시_상점이_OPEN이_아니면_RESERVATION_STORE_NOT_RESERVABLE() {
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.TEMP_CLOSED);
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(10L, LocalDate.now().plusDays(1), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_STORE_NOT_RESERVABLE);
    }

    @Test
    void 가용_슬롯_조회_시_예약설정_없으면_RESERVATION_SETTING_NOT_FOUND() {
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(10L, LocalDate.now().plusDays(1), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_SETTING_NOT_FOUND);
    }

    @Test
    void 가용_슬롯_조회_시_예약기능_비활성화면_RESERVATION_DISABLED() {
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        StoreVisitReservationSetting disabledSetting = StoreVisitReservationSetting.createDefault(store);
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(disabledSetting));

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(10L, LocalDate.now().plusDays(1), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_DISABLED);
    }

    @Test
    void 가용_슬롯_조회_시_과거_날짜면_RESERVATION_TIME_UNAVAILABLE() {
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(10L, LocalDate.now().minusDays(1), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
    }

    @Test
    void 가용_슬롯_조회_시_당일예약_불허인데_오늘이면_RESERVATION_TIME_UNAVAILABLE() {
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(10L, owner, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, false, 30, LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(storeRepository.findById(eq(10L))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(10L))).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(10L, LocalDate.now(), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_TIME_UNAVAILABLE);
    }

    @Test
    void 가용_슬롯_조회_시_영업시간_미설정이면_RESERVATION_STORE_BUSINESS_HOURS_NOT_SET() {
        Long storeId = 10L;
        LocalDate visitDate = LocalDate.now().plusDays(7);
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(storeId, owner, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(visitDate.getDayOfWeek());

        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(storeId))).thenReturn(Optional.of(setting));
        when(storeBusinessHourRepository.findByStore_StoreIdAndDayOfWeek(eq(storeId), eq(dayOfWeek)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(storeId, visitDate, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_STORE_BUSINESS_HOURS_NOT_SET);
    }

    @Test
    void 가용_슬롯_조회_시_휴무일이면_RESERVATION_STORE_CLOSED_DAY() {
        Long storeId = 10L;
        LocalDate visitDate = LocalDate.now().plusDays(7);
        Account owner = createAccount(1L, "사장", "owner");
        Store store = createStore(storeId, owner, StoreStatus.OPEN);
        StoreVisitReservationSetting setting = createEnabledSetting(store);
        StoreDayOfWeek dayOfWeek = StoreDayOfWeek.from(visitDate.getDayOfWeek());
        StoreBusinessHour closedHour = StoreBusinessHour.create(store, dayOfWeek, true, null, null);

        when(storeRepository.findById(eq(storeId))).thenReturn(Optional.of(store));
        when(storeVisitReservationSettingRepository.findByStore_StoreId(eq(storeId))).thenReturn(Optional.of(setting));
        when(storeBusinessHourRepository.findByStore_StoreIdAndDayOfWeek(eq(storeId), eq(dayOfWeek)))
                .thenReturn(Optional.of(closedHour));

        assertThatThrownBy(() -> visitReservationService.getAvailableTimeSlots(storeId, visitDate, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_STORE_CLOSED_DAY);
    }
}
