package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiAdExposureLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AiAdExposureLogRepository extends JpaRepository<AiAdExposureLog, Long> {

    long countByStore_StoreIdAndExposedAtAfter(Long storeId, LocalDateTime after);
}
