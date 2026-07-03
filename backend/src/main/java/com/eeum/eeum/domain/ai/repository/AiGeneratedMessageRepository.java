package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiGeneratedMessageRepository extends JpaRepository<AiGeneratedMessage, Long> {

    Page<AiGeneratedMessage> findByStore_StoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    Page<AiGeneratedMessage> findByStore_StoreIdAndTypeOrderByCreatedAtDesc(
            Long storeId, AiMessageType type, Pageable pageable);

    long countByStore_StoreIdAndTypeAndCreatedAtAfter(Long storeId, AiMessageType type, LocalDateTime after);

    long countByStore_StoreIdAndTypeAndStatusAndSentAtAfter(
            Long storeId, AiMessageType type, AiMessageStatus status, LocalDateTime after);

    long countByStore_StoreIdAndStatusAndSentAtAfter(Long storeId, AiMessageStatus status, LocalDateTime after);

    List<AiGeneratedMessage> findByStore_StoreIdAndCreatedAtAfter(Long storeId, LocalDateTime after);
}
