package com.eeum.eeum.domain.external.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 국토교통부_건물에너지 전기에너지(15054214) 파일 Import 통계 — 법정동/월 단위로 집계 적재
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_building_energy_stat", indexes = {
        @Index(name = "idx_ext_building_period_region", columnList = "source_period, sigungu")
})
public class ExternalBuildingEnergyStat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_building_energy_stat_id")
    private Long externalBuildingEnergyStatId;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_id", nullable = false, length = 20)
    private String sourceId;

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

    // 법정동/월 단위 집계 사용량 (대용량 원본을 그대로 넣지 않는다)
    @Column(name = "usage_kwh", precision = 18, scale = 2)
    private BigDecimal usageKwh;

    @Column(name = "building_count")
    private Long buildingCount;

    @Column(name = "building_type", length = 50)
    private String buildingType;

    @Column(name = "source_updated_at")
    private LocalDate sourceUpdatedAt;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    public static ExternalBuildingEnergyStat create(
            String sourceName, String sourceId, String sourcePeriod,
            String sido, String sigungu, String legalDongCode, String legalDongName,
            BigDecimal usageKwh, Long buildingCount, String buildingType, LocalDate sourceUpdatedAt
    ) {
        ExternalBuildingEnergyStat stat = new ExternalBuildingEnergyStat();
        stat.sourceName = sourceName;
        stat.sourceId = sourceId;
        stat.sourcePeriod = sourcePeriod;
        stat.sido = sido;
        stat.sigungu = sigungu;
        stat.legalDongCode = legalDongCode;
        stat.legalDongName = legalDongName;
        stat.usageKwh = usageKwh;
        stat.buildingCount = buildingCount;
        stat.buildingType = buildingType;
        stat.sourceUpdatedAt = sourceUpdatedAt;
        stat.importedAt = LocalDateTime.now();
        return stat;
    }
}
