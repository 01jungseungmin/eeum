package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiMessageDeliveryRepository extends JpaRepository<AiMessageDelivery, Long> {

    // 중복 발송 방지 — 이미 발송 기록이 있으면 재발송하지 않는다
    boolean existsByMessage_AiGeneratedMessageId(Long messageId);

    // 전환 추적 — 고객이 최근 받은 발송 성공 메시지 조회 (최신순)
    List<AiMessageDelivery> findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
            Long targetAccountId, Long storeId, AiDeliveryStatus status, LocalDateTime after);

    long countByStore_StoreIdAndStatusAndSentAtAfter(Long storeId, AiDeliveryStatus status, LocalDateTime after);
}
