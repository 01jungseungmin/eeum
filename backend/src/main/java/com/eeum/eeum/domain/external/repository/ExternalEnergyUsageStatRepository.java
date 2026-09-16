package com.eeum.eeum.domain.external.repository;

import com.eeum.eeum.domain.external.entity.ExternalEnergyUsageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExternalEnergyUsageStatRepository extends JpaRepository<ExternalEnergyUsageStat, Long> {

    // 중복 Import 멱등 처리 — 같은 데이터/기준월을 재적재 전 삭제 후 삽입
    @Modifying
    @Query("DELETE FROM ExternalEnergyUsageStat s WHERE s.sourceId = :sourceId AND s.sourcePeriod = :sourcePeriod")
    void deleteBySourceIdAndSourcePeriod(@Param("sourceId") String sourceId,
                                         @Param("sourcePeriod") String sourcePeriod);

    List<ExternalEnergyUsageStat> findBySigunguContainingOrderBySourcePeriodDesc(String sigungu);

    List<ExternalEnergyUsageStat> findTop12ByOrderBySourcePeriodDesc();
}
