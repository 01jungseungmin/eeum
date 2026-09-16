package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.entity.AiSavingPlanItem;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final AiActionLogRepository aiActionLogRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public AiSavingPlanResponseDto createSavingPlanInTx(Store store) {
        AiOwnerMetricSnapshot snapshot = AiOwnerMetricSnapshot.load(aiOwnerMetricInputRepository, store.getStoreId());
        List<PendingItem> pendingItems = buildPendingItems(snapshot);
        BigDecimal total = pendingItems.isEmpty() ? null : sumAmounts(pendingItems);

        AiSavingPlan plan = aiSavingPlanRepository
                .findFirstByStore_StoreIdAndStatusOrderByCreatedAtDesc(store.getStoreId(), AiSavingPlanStatus.DRAFT)
                .orElseGet(() -> AiSavingPlan.create(store, "", null));
        plan.recreateDraft("이번 달 전력 절감 계획", total);

        if (pendingItems.isEmpty()) {
            // 설비/사용량 입력이 전혀 없으면 실측값 입력을 유도하는 안내 항목만 생성
            plan.addItem(AiSavingPlanItem.create(
                    plan, "전기요금 고지서 기준 실측값 입력", "쉬움", "즉시", null, false));
        } else {
            pendingItems.forEach(item -> plan.addItem(AiSavingPlanItem.create(
                    plan, item.title(), item.difficulty(), item.startTiming(), item.amount(), item.selected())));
        }

        AiSavingPlan saved = aiSavingPlanRepository.save(plan);
        return AiSavingPlanResponseDto.from(saved, "이번 주부터 순차 실행 권장");
    }

    // 절감 계획 항목 생성 규칙 — 설비 보유 여부로 항목 생성을 결정하고, 요금 실측값이 있을 때만 예상 절감액을 계산한다
    private List<PendingItem> buildPendingItems(AiOwnerMetricSnapshot snapshot) {
        BigDecimal monthlyBill = snapshot.monthlyPowerBill();
        List<PendingItem> items = new ArrayList<>();

        if (snapshot.hasAirConditioner()) {
            // 영업시간이 길수록(>=10시간) 냉방 부하가 커 절감 여지가 더 크다고 본다
            int coolingPercent = snapshot.isLongOpenHours() ? 8 : 5;
            items.add(new PendingItem("냉방 시간대 관리", "쉬움", "즉시",
                    percentOf(monthlyBill, coolingPercent), true));
        }

        if (snapshot.hasRefrigerator()) {
            items.add(new PendingItem("냉장 설비 점검", "보통", "이번 주",
                    percentOf(monthlyBill, 3), true));
        }

        if (snapshot.hasAirConditioner() || snapshot.hasRefrigerator()) {
            items.add(new PendingItem("고효율 설비 교체 검토", "어려움", "이번 달",
                    percentOf(monthlyBill, 8), false));
        }

        if (snapshot.hasGasEquipment()) {
            items.add(new PendingItem("가스 밸브·호스 점검", "쉬움", "이번 주",
                    percentOf(snapshot.monthlyGasBill(), 5), true));
        }

        if (snapshot.isHighPowerUsage()) {
            items.add(new PendingItem("피크 시간대 전력 사용 분산", "보통", "이번 달",
                    percentOf(monthlyBill, 4), false));
        }

        return items;
    }

    private BigDecimal sumAmounts(List<PendingItem> items) {
        BigDecimal total = items.stream()
                .map(PendingItem::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    private record PendingItem(
            String title, String difficulty, String startTiming, BigDecimal amount, boolean selected) {
    }

    @Transactional
    public AiSavingPlanResponseDto savePlanInTx(Store store, Long ownerAccountId, Long savingPlanId) {
        AiSavingPlan plan = (savingPlanId != null
                ? aiSavingPlanRepository.findByAiSavingPlanIdAndStore_StoreId(savingPlanId, store.getStoreId())
                : aiSavingPlanRepository.findFirstByStore_StoreIdOrderByCreatedAtDesc(store.getStoreId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_SAVING_PLAN_NOT_FOUND));
        plan.save();
        Account owner = accountRepository.getReferenceById(ownerAccountId);
        aiActionLogRepository.save(AiActionLog.record(
                store, owner, AiActionType.SAVING_PLAN_SAVED,
                "AI_SAVING_PLAN", plan.getAiSavingPlanId(), "절감 계획 저장"));
        return AiSavingPlanResponseDto.from(plan, "이번 주부터 순차 실행 권장");
    }

    private BigDecimal percentOf(BigDecimal base, int percent) {
        if (base == null) {
            return null;
        }
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
