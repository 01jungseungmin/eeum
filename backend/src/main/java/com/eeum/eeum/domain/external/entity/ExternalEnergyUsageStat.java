package com.eeum.eeum.domain.external.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 한국전력공사_용도별 전력사용량(15101311) 파일 Import 통계
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_energy_usage_stat", indexes = {
        @Index(name = "idx_ext_energy_period_region", columnList = "source_period, sigungu")
})
public class ExternalEnergyUsageStat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_energy_usage_stat_id")
    private Long externalEnergyUsageStatId;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_id", nullable = false, length = 20)
    private String sourceId;

    // 기준년월 (예: 2026-05)
    @Column(name = "source_period", nullable = false, length = 10)
    private String sourcePeriod;

    @Column(name = "sido", length = 30)
    private String sido;

    @Column(name = "sigungu", length = 30)
    private String sigungu;

    @Column(name = "legal_dong_code", length = 20)
    private String legalDongCode;

    @Column(name = "legal_dong_name", length = 50)
    private String legalDongName;

    // 용도/계약종별 (예: 일반용, 산업용)
    @Column(name = "usage_type", length = 50)
    private String usageType;

    @Column(name = "usage_kwh", precision = 18, scale = 2)
    private BigDecimal usageKwh;

    @Column(name = "charge_amount", precision = 18, scale = 2)
    private BigDecimal chargeAmount;

    @Column(name = "customer_count")
    private Long customerCount;

    @Column(name = "average_unit_price", precision = 12, scale = 2)
    private BigDecimal averageUnitPrice;

    @Column(name = "source_updated_at")
    private LocalDate sourceUpdatedAt;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    public static ExternalEnergyUsageStat create(
            String sourceName, String sourceId, String sourcePeriod,
            String sido, String sigungu, String legalDongCode, String legalDongName,
            String usageType, BigDecimal usageKwh, BigDecimal chargeAmount,
            Long customerCount, BigDecimal averageUnitPrice, LocalDate sourceUpdatedAt
    ) {
        ExternalEnergyUsageStat stat = new ExternalEnergyUsageStat();
        stat.sourceName = sourceName;
        stat.sourceId = sourceId;
        stat.sourcePeriod = sourcePeriod;
        stat.sido = sido;
        stat.sigungu = sigungu;
        stat.legalDongCode = legalDongCode;
        stat.legalDongName = legalDongName;
        stat.usageType = usageType;
        stat.usageKwh = usageKwh;
        stat.chargeAmount = chargeAmount;
        stat.customerCount = customerCount;
        stat.averageUnitPrice = averageUnitPrice;
        stat.sourceUpdatedAt = sourceUpdatedAt;
        stat.importedAt = LocalDateTime.now();
        return stat;
    }
}
