package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AiActionLogRepository extends JpaRepository<AiActionLog, Long> {

    long countByStore_StoreIdAndActionTypeAndCreatedAtAfter(
            Long storeId, AiActionType actionType, LocalDateTime after);
}
