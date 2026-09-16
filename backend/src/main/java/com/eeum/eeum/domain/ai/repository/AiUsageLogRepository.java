package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    long countByStore_StoreIdAndYearMonth(Long storeId, String yearMonth);
}
