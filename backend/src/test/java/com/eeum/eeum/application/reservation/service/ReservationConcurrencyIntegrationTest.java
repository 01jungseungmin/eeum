package com.eeum.eeum.application.reservation.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.reservation.dto.request.StoreTableConfigRequestDto;
import com.eeum.eeum.application.reservation.dto.request.VisitReservationCreateRequestDto;
import com.eeum.eeum.application.reservation.dto.response.StoreTableResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.reservation.entity.StoreTable;
import com.eeum.eeum.domain.reservation.entity.StoreVisitReservationSetting;
import com.eeum.eeum.domain.reservation.repository.StoreTableRepository;
import com.eeum.eeum.domain.reservation.repository.StoreVisitReservationSettingRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.reservation.repository.VisitReservationTimeSlotRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 도메인 통합 테스트.
 *
 * <p><b>검증 대상</b>
 * <ol>
 *   <li>configureTablesInternal: 기존 테이블 deactivate() + saveAll 이 같은 트랜잭션 안에서
 *       Hibernate flush 없이 올바르게 반영되는지 — DB 재조회로 확인</li>
 *   <li>createReservation 동시 요청: 동일 슬롯에 1개 테이블이 있을 때 Redis 분산 락이
 *       실제로 작동해 두 요청 중 하나만 성공하는지 검증</li>
 * </ol>
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ReservationConcurrencyIntegrationTest extends IntegrationTestSupport {



    private final VisitReservationService visitReservationService;
    private final StoreTableService storeTableService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final StoreVisitReservationSettingRepository storeVisitReservationSettingRepository;
    private final StoreTableRepository storeTableRepository;
    private final VisitReservationRepository visitReservationRepository;
    private final VisitReservationTimeSlotRepository visitReservationTimeSlotRepository;
    private final NotificationRepository notificationRepository;

    private Long ownerAccountId;
    private Long customerAId;
    private Long customerBId;
    private Long storeId;

    // 내일 날짜 슬롯 — 과거/당일 제약을 피하기 위해 tomorrow 고정
    private static final LocalDate VISIT_DATE = LocalDate.now().plusDays(1);
    private static final LocalTime VISIT_TIME = LocalTime.of(10, 0);

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(
                Account.createOwner("owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        ownerAccountId = owner.getAccountId();

        Account customerA = accountRepository.save(
                Account.createUser("cust-a@test.com", "encoded_pw", "고객A", "custA", "010-2222-2222"));
        customerAId = customerA.getAccountId();

        Account customerB = accountRepository.save(
                Account.createUser("cust-b@test.com", "encoded_pw", "고객B", "custB", "010-3333-3333"));
        customerBId = customerB.getAccountId();

        Store store = Store.createForOwnerSignup(owner, "테스트상점", "서울시", "02-0000-0000");
        store.open();
        store = storeRepository.save(store);
        storeId = store.getStoreId();

        // 방문 날짜의 요일에 대한 영업시간 등록 (09:00 ~ 18:00, 영업일)
        StoreDayOfWeek visitDayOfWeek = StoreDayOfWeek.from(VISIT_DATE.getDayOfWeek());
        storeBusinessHourRepository.save(
                StoreBusinessHour.create(store, visitDayOfWeek, false,
                        LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // 예약 설정 (활성화, 30분 슬롯, 당일 예약 허용)
        StoreVisitReservationSetting setting = StoreVisitReservationSetting.createDefault(store);
        setting.update(true, 30, true, 60, LocalTime.of(9, 0), LocalTime.of(18, 0));
        storeVisitReservationSettingRepository.save(setting);

        // 4인석 테이블 1개
        storeTableRepository.save(StoreTable.create(store, 4, "4인석-1"));
    }

    @AfterEach
    void tearDown() {
        visitReservationTimeSlotRepository.deleteAll();
        visitReservationRepository.deleteAll();
        storeTableRepository.deleteAll();
        storeVisitReservationSettingRepository.deleteAll();
        storeBusinessHourRepository.deleteAll();
        storeRepository.deleteAll();
        notificationRepository.deleteAll(); // account FK 참조 → account 삭제 전 먼저 정리
    }

    // ──────────────── 시나리오 1: configureTablesInternal dirty-marking ────────────────

    @Test
    void 테이블_구성변경_기존테이블_비활성화되고_신규테이블이_DB에_반영된다() {
        // given: 기존 4인석 1개가 이미 setUp에서 저장됨
        List<StoreTable> before = storeTableRepository
                .findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId);
        assertThat(before).hasSize(1);
        Long oldTableId = before.get(0).getStoreTableId();

        // when: 2인석 2개로 재구성
        StoreTableConfigRequestDto request = buildConfigRequest(2, 2);
        List<StoreTableResponseDto> result = storeTableService.configureTables(ownerAccountId, request);

        // then 1: 반환 DTO
        assertThat(result).hasSize(2);
        result.forEach(dto -> assertThat(dto.getCapacity()).isEqualTo(2));

        // then 2: DB 재조회 — 기존 테이블 비활성화 반영 여부 확인 (Hibernate flush 타이밍 검증)
        StoreTable oldInDb = storeTableRepository.findById(oldTableId).orElseThrow();
        assertThat(oldInDb.isActive())
                .as("기존 테이블은 DB에서 active=false 로 반영되어야 한다")
                .isFalse();

        // then 3: 신규 테이블이 DB에 저장되었는지 확인
        List<StoreTable> newTables = storeTableRepository
                .findByStore_StoreIdAndActiveTrueOrderByCapacityAscStoreTableIdAsc(storeId);
        assertThat(newTables).hasSize(2);
        newTables.forEach(t -> assertThat(t.getCapacity()).isEqualTo(2));
    }

    // ──────────────── 시나리오 2: createReservation 동시 요청 ────────────────

    @Test
    void 동일_슬롯에_동시_예약_요청_시_하나만_성공하고_나머지는_테이블_없음() throws InterruptedException {
        // given: 4인석 테이블 1개, 동일 날짜/시간 슬롯
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(2);

        AtomicBoolean successA = new AtomicBoolean(false);
        AtomicBoolean successB = new AtomicBoolean(false);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        VisitReservationCreateRequestDto requestA = buildReservationRequest(VISIT_DATE, VISIT_TIME, 2);
        VisitReservationCreateRequestDto requestB = buildReservationRequest(VISIT_DATE, VISIT_TIME, 2);

        Thread threadA = new Thread(() -> {
            try {
                startLatch.await();
                visitReservationService.createReservation(customerAId, storeId, requestA);
                successA.set(true);
            } catch (Exception e) {
                errorA.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                startLatch.await();
                visitReservationService.createReservation(customerBId, storeId, requestB);
                successB.set(true);
            } catch (Exception e) {
                errorB.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        threadA.start();
        threadB.start();
        startLatch.countDown(); // 동시 출발

        // when
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);

        // then 1: 양쪽 모두 15초 내에 완료
        assertThat(completed).isTrue();

        // then 2: 정확히 하나만 성공
        boolean exactlyOneSuccess = successA.get() ^ successB.get();
        assertThat(exactlyOneSuccess)
                .as("동일 슬롯/1테이블 환경에서는 정확히 하나의 요청만 성공해야 한다")
                .isTrue();

        // then 3: 실패한 요청은 RESERVATION_TABLE_UNAVAILABLE 또는 LOCK_RESERVATION_FAILED
        Throwable failure = errorA.get() != null ? errorA.get() : errorB.get();
        assertThat(failure).isInstanceOf(BusinessException.class);
        ErrorCode failCode = ((BusinessException) failure).getErrorCode();
        assertThat(failCode)
                .as("실패 원인은 테이블 부족 또는 락 실패여야 한다")
                .isIn(ErrorCode.RESERVATION_TABLE_UNAVAILABLE, ErrorCode.LOCK_RESERVATION_FAILED);

        // then 4: DB에 예약이 정확히 1건 저장됨
        long savedCount = visitReservationRepository.count();
        assertThat(savedCount)
                .as("DB에 예약이 정확히 1건이어야 한다")
                .isEqualTo(1L);
    }

    // ──────────────── 헬퍼 ────────────────

    private StoreTableConfigRequestDto buildConfigRequest(int capacity, int count) {
        StoreTableConfigRequestDto.TableItem item = new StoreTableConfigRequestDto.TableItem();
        ReflectionTestUtils.setField(item, "capacity", capacity);
        ReflectionTestUtils.setField(item, "count", count);
        StoreTableConfigRequestDto request = new StoreTableConfigRequestDto();
        ReflectionTestUtils.setField(request, "tables", List.of(item));
        return request;
    }

    private VisitReservationCreateRequestDto buildReservationRequest(
            LocalDate date, LocalTime time, Integer partySize
    ) {
        VisitReservationCreateRequestDto dto = new VisitReservationCreateRequestDto();
        ReflectionTestUtils.setField(dto, "visitDate", date);
        ReflectionTestUtils.setField(dto, "visitTime", time);
        ReflectionTestUtils.setField(dto, "partySize", partySize);
        ReflectionTestUtils.setField(dto, "requestMessage", null);
        return dto;
    }
}
