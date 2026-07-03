package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.entity.AiSavingPlanItem;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * 절감 계획 생성 Executor — @Transactional이 Redis 락 안에서 시작되도록 별도 Bean으로 분리.
 * AiOperationRiskService가 aiSavingPlan 락을 먼저 잡고 이 Executor를 호출해
 * check-then-insert를 단일 TX로 처리하고 커밋 후 락 해제 순서를 보장한다.
 * DRAFT 중복 생성을 방지한다.
 */
@Service
@RequiredArgsConstructor
public class AiSavingPlanCommandExecutor {

    private final AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    private final AiSavingPlanRepository aiSavingPlanRepository;

    @Transactional
    public AiSavingPlanResponseDto createSavingPlanInTx(Store store) {
        BigDecimal monthlyBill = findLatestMetricValue(store.getStoreId(), AiMetricType.MONTHLY_POWER_BILL);
        BigDecimal coolingSaving = percentOf(monthlyBill, 5);
        BigDecimal fridgeSaving = percentOf(monthlyBill, 3);
        BigDecimal equipmentSaving = percentOf(monthlyBill, 8);

        BigDecimal total = (coolingSaving != null && fridgeSaving != null)
                ? coolingSaving.add(fridgeSaving)
                : null;

        AiSavingPlan plan = aiSavingPlanRepository
                .findFirstByStore_StoreIdAndStatusOrderByCreatedAtDesc(store.getStoreId(), AiSavingPlanStatus.DRAFT)
                .orElseGet(() -> AiSavingPlan.create(store, "", null));
        plan.recreateDraft("이번 달 전력 절감 계획", total);
        plan.addItem(AiSavingPlanItem.create(plan, "냉방 시간대 관리", "쉬움", "즉시", coolingSaving, true));
        plan.addItem(AiSavingPlanItem.create(plan, "냉장 설비 점검", "보통", "이번 주", fridgeSaving, true));
        plan.addItem(AiSavingPlanItem.create(plan, "고효율 설비 교체 검토", "어려움", "이번 달", equipmentSaving, false));
        AiSavingPlan saved = aiSavingPlanRepository.save(plan);

        return AiSavingPlanResponseDto.from(saved, "이번 주부터 순차 실행 권장");
    }

    private BigDecimal findLatestMetricValue(Long storeId, AiMetricType metricType) {
        return aiOwnerMetricInputRepository
                .findByStore_StoreIdAndMetricTypeAndYearMonth(storeId, metricType, YearMonth.now().toString())
                .or(() -> aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                        storeId, metricType, YearMonth.now().minusMonths(1).toString()))
                .map(AiOwnerMetricInput::getValue)
                .orElse(null);
    }

    private BigDecimal percentOf(BigDecimal base, int percent) {
        if (base == null) {
            return null;
        }
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
