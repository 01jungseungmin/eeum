package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_owner_metric_input", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_metric_store_type_month",
                columnNames = {"store_id", "metric_type", "metric_year_month"})
})
public class AiOwnerMetricInput extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_owner_metric_input_id")
    private Long aiOwnerMetricInputId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 30)
    private AiMetricType metricType;

    @Column(name = "metric_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    // 형식: yyyy-MM (예: 2026-07)
    @Column(name = "metric_year_month", nullable = false, length = 7)
    private String yearMonth;

    public static AiOwnerMetricInput create(Store store, AiMetricType metricType, BigDecimal value, String yearMonth) {
        AiOwnerMetricInput input = new AiOwnerMetricInput();
        input.store = store;
        input.metricType = metricType;
        input.value = value;
        input.yearMonth = yearMonth;
        return input;
    }

    public void updateValue(BigDecimal value) {
        this.value = value;
    }
}
