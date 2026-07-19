package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiAdClickLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AiAdClickLogRepository extends JpaRepository<AiAdClickLog, Long> {

    long countByStore_StoreIdAndClickedAtAfter(Long storeId, LocalDateTime after);
}
