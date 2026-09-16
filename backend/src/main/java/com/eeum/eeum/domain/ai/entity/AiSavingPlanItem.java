package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_saving_plan_item")
// 절감 계획의 개별 실천 항목 저장
public class AiSavingPlanItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_saving_plan_item_id")
    private Long aiSavingPlanItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_saving_plan_id", nullable = false)
    private AiSavingPlan savingPlan;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 예: 쉬움 / 보통 / 어려움
    @Column(name = "difficulty", nullable = false, length = 20)
    private String difficulty;

    // 예: 즉시 / 이번 주 / 이번 달
    @Column(name = "start_timing", nullable = false, length = 30)
    private String startTiming;

    @Column(name = "expected_monthly_saving_amount", precision = 12, scale = 2)
    private BigDecimal expectedMonthlySavingAmount;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    public static AiSavingPlanItem create(
            AiSavingPlan savingPlan,
            String title,
            String difficulty,
            String startTiming,
            BigDecimal expectedMonthlySavingAmount,
            boolean selected
    ) {
        AiSavingPlanItem item = new AiSavingPlanItem();
        item.savingPlan = savingPlan;
        item.title = title;
        item.difficulty = difficulty;
        item.startTiming = startTiming;
        item.expectedMonthlySavingAmount = expectedMonthlySavingAmount;
        item.selected = selected;
        return item;
    }
}
