package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_saving_plan")
// 절감 계획 묶음 저장
public class AiSavingPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_saving_plan_id")
    private Long aiSavingPlanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "expected_monthly_saving_amount", precision = 12, scale = 2)
    private BigDecimal expectedMonthlySavingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiSavingPlanStatus status;

    @OneToMany(mappedBy = "savingPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiSavingPlanItem> items = new ArrayList<>();

    public static AiSavingPlan create(Store store, String title, BigDecimal expectedMonthlySavingAmount) {
        AiSavingPlan plan = new AiSavingPlan();
        plan.store = store;
        plan.title = title;
        plan.expectedMonthlySavingAmount = expectedMonthlySavingAmount;
        plan.status = AiSavingPlanStatus.DRAFT;
        return plan;
    }

    public void addItem(AiSavingPlanItem item) {
        this.items.add(item);
    }

    public void save() {
        if (this.status == AiSavingPlanStatus.SAVED) {
            return;
        }
        this.status = AiSavingPlanStatus.SAVED;
    }

    public void recreateDraft(String title, BigDecimal expectedMonthlySavingAmount) {
        this.title = title;
        this.expectedMonthlySavingAmount = expectedMonthlySavingAmount;
        this.status = AiSavingPlanStatus.DRAFT;
        this.items.clear();
    }
}
