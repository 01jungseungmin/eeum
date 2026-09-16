package com.eeum.eeum.domain.sanction.repository;

import com.eeum.eeum.domain.sanction.entity.SanctionHistory;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionHistoryRepository extends JpaRepository<SanctionHistory, Long> {

    Page<SanctionHistory> findByTargetTypeAndTargetIdOrderByCreatedAtDescSanctionHistoryIdDesc(
            SanctionTargetType targetType,
            Long targetId,
            Pageable pageable
    );
}
