package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// cleanupOldAiChatMessages : 매일 새벽 3시 — 90일 이전 AI 채팅 메시지 물리 삭제

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCleanupScheduler {

    private static final int CHAT_MESSAGE_RETENTION_DAYS = 90;

    private final AiChatMessageRepository aiChatMessageRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldAiChatMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(CHAT_MESSAGE_RETENTION_DAYS);
        int deleted = aiChatMessageRepository.deleteOldMessages(threshold);
        log.info("[AiCleanup] 오래된 AI 채팅 메시지 삭제: count={}, threshold={}", deleted, threshold);
    }
}
