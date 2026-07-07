package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.entity.AiSavingPlanItem;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSavingPlanCommandExecutorTest {

    @InjectMocks
    private AiSavingPlanCommandExecutor executor;

    @Mock private AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    @Mock private AiSavingPlanRepository aiSavingPlanRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private AccountRepository accountRepository;

    private static final Long STORE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    private void stubNoDraftAndSaveNoOp(Store store) {
        when(aiSavingPlanRepository.findFirstByStore_StoreIdAndStatusOrderByCreatedAtDesc(STORE_ID, AiSavingPlanStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(aiSavingPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubMetricInputs(Store store, AiOwnerMetricInput... inputs) {
        when(aiOwnerMetricInputRepository
                        .findByStore_StoreIdAndMetricTypeInAndYearMonthIn(eq(STORE_ID), any(), any()))
                .thenReturn(List.of(inputs));
    }

    private AiOwnerMetricInput metric(Store store, AiMetricType type, BigDecimal value) {
        return AiOwnerMetricInput.create(store, type, value, YearMonth.now().toString());
    }

    private AiSavingPlanResponseDto.SavingPlanItemDto findItem(AiSavingPlanResponseDto response, String title) {
        return response.getItems().stream()
                .filter(item -> item.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("item not found: " + title));
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 입력값이_전혀_없으면_실측값_입력_안내_항목만_생성되고_총액은_null이다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store);

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("전기요금 고지서 기준 실측값 입력");
        assertThat(response.getTotalExpectedSavingAmount()).isNull();
    }

    @Test
    void 에어컨_보유_및_전기요금이_있으면_냉방_및_고효율_설비_항목이_생성되고_절감액이_계산된다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store,
                metric(store, AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000")),
                metric(store, AiMetricType.AIR_CONDITIONER_COUNT, new BigDecimal("2")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        var cooling = findItem(response, "냉방 시간대 관리");
        assertThat(cooling.getExpectedMonthlySavingAmount()).isEqualByComparingTo("5000"); // 5%
        var equipment = findItem(response, "고효율 설비 교체 검토");
        assertThat(equipment.getExpectedMonthlySavingAmount()).isEqualByComparingTo("8000"); // 8%
        assertThat(response.getItems())
                .extracting(AiSavingPlanResponseDto.SavingPlanItemDto::getTitle)
                .doesNotContain("냉장 설비 점검");
        assertThat(response.getTotalExpectedSavingAmount()).isEqualByComparingTo("13000");
    }

    @Test
    void 영업시간이_10시간_이상이면_냉방_절감률이_더_높게_계산된다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store,
                metric(store, AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000")),
                metric(store, AiMetricType.AIR_CONDITIONER_COUNT, new BigDecimal("1")),
                metric(store, AiMetricType.OPEN_HOURS_PER_DAY, new BigDecimal("12")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        var cooling = findItem(response, "냉방 시간대 관리");
        assertThat(cooling.getExpectedMonthlySavingAmount()).isEqualByComparingTo("8000"); // 8% (10시간 이상)
    }

    @Test
    void 설비는_있지만_전기요금이_없으면_항목은_생성되되_예상_절감액은_null이다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store, metric(store, AiMetricType.REFRIGERATOR_COUNT, new BigDecimal("1")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        var fridge = findItem(response, "냉장 설비 점검");
        assertThat(fridge.getExpectedMonthlySavingAmount()).isNull();
        assertThat(response.getTotalExpectedSavingAmount()).isNull();
    }

    @Test
    void 가스_설비가_있으면_가스_점검_항목이_생성된다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store,
                metric(store, AiMetricType.GAS_EQUIPMENT_COUNT, new BigDecimal("1")),
                metric(store, AiMetricType.MONTHLY_GAS_BILL, new BigDecimal("50000")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        var gas = findItem(response, "가스 밸브·호스 점검");
        assertThat(gas.getExpectedMonthlySavingAmount()).isEqualByComparingTo("2500"); // 5%
    }

    @Test
    void 전력_사용량이_700_이상이면_피크_시간대_분산_항목이_생성된다() {
        // given
        Store store = stubStore();
        stubNoDraftAndSaveNoOp(store);
        stubMetricInputs(store,
                metric(store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("750")),
                metric(store, AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        assertThat(response.getItems())
                .extracting(AiSavingPlanResponseDto.SavingPlanItemDto::getTitle)
                .contains("피크 시간대 전력 사용 분산");
    }

    @Test
    void 기존_DRAFT_계획의_이전_항목은_재생성_시_초기화된다() {
        // given
        Store store = stubStore();
        AiSavingPlan existingPlan = AiSavingPlan.create(store, "이전 계획", new BigDecimal("999"));
        existingPlan.addItem(AiSavingPlanItem.create(existingPlan, "이전 항목", "쉬움", "즉시", BigDecimal.TEN, true));
        when(aiSavingPlanRepository.findFirstByStore_StoreIdAndStatusOrderByCreatedAtDesc(STORE_ID, AiSavingPlanStatus.DRAFT))
                .thenReturn(Optional.of(existingPlan));
        when(aiSavingPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubMetricInputs(store, metric(store, AiMetricType.AIR_CONDITIONER_COUNT, new BigDecimal("1")));

        // when
        AiSavingPlanResponseDto response = executor.createSavingPlanInTx(store);

        // then
        assertThat(response.getItems())
                .extracting(AiSavingPlanResponseDto.SavingPlanItemDto::getTitle)
                .doesNotContain("이전 항목");
    }
}
