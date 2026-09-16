package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiGeneratedMessageRepository extends JpaRepository<AiGeneratedMessage, Long> {

    Page<AiGeneratedMessage> findByStore_StoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    // 초안 보관 개수 캡 검증용 — 타입별 DRAFT 개수
    long countByStore_StoreIdAndTypeAndStatus(Long storeId, AiMessageType type, AiMessageStatus status);

    // 캡 초과 + 삭제 확인 시 삭제 대상(가장 오래된 DRAFT) 조회
    Optional<AiGeneratedMessage> findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(
            Long storeId, AiMessageType type, AiMessageStatus status);

    Page<AiGeneratedMessage> findByStore_StoreIdAndTypeOrderByCreatedAtDesc(
            Long storeId, AiMessageType type, Pageable pageable);

    long countByStore_StoreIdAndTypeAndCreatedAtAfter(Long storeId, AiMessageType type, LocalDateTime after);

    long countByStore_StoreIdAndTypeAndStatusAndSentAtAfter(
            Long storeId, AiMessageType type, AiMessageStatus status, LocalDateTime after);

    long countByStore_StoreIdAndStatusAndSentAtAfter(Long storeId, AiMessageStatus status, LocalDateTime after);

    List<AiGeneratedMessage> findByStore_StoreIdAndCreatedAtAfter(Long storeId, LocalDateTime after);

    // 예약 발송 스케줄러 — 발송 시각이 지난 SCHEDULED 메시지 조회 (limit은 Pageable로 제한)
    List<AiGeneratedMessage> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            AiMessageStatus status, LocalDateTime now, org.springframework.data.domain.Pageable pageable);
}
