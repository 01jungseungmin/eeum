package com.eeum.eeum.domain.external.repository;

import com.eeum.eeum.domain.external.entity.ExternalBuildingEnergyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExternalBuildingEnergyStatRepository extends JpaRepository<ExternalBuildingEnergyStat, Long> {

    @Modifying
    @Query("DELETE FROM ExternalBuildingEnergyStat s WHERE s.sourceId = :sourceId AND s.sourcePeriod = :sourcePeriod")
    void deleteBySourceIdAndSourcePeriod(@Param("sourceId") String sourceId,
                                         @Param("sourcePeriod") String sourcePeriod);

    List<ExternalBuildingEnergyStat> findBySigunguContainingOrderBySourcePeriodDesc(String sigungu);
}
