package com.eeum.eeum.domain.settlement.repository;

import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WeeklySettlementRepository extends JpaRepository<WeeklySettlement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM WeeklySettlement s
        WHERE s.weeklySettlementId = :weeklySettlementId
    """)
    Optional<WeeklySettlement> findByIdWithPessimisticLock(
            @Param("weeklySettlementId") Long weeklySettlementId
    );
}
