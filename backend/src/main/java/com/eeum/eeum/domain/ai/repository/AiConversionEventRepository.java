package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiConversionEvent;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiConversionEventRepository extends JpaRepository<AiConversionEvent, Long> {

    boolean existsByMessage_AiGeneratedMessageIdAndAccountId(Long messageId, Long accountId);

    long countByStore_StoreIdAndConversionTypeAndConvertedAtAfter(
            Long storeId, AiConversionType conversionType, LocalDateTime after);

    List<AiConversionEvent> findByStore_StoreIdAndConvertedAtBetween(
            Long storeId, LocalDateTime from, LocalDateTime to);
}
