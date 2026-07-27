package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 생성 문구 초안 저장(DRAFT insert + 액션 로그)만 전담하는 짧은 트랜잭션.
 * LLM 호출(외부 HTTP, 수 초~15초 타임아웃)은 이 메서드 호출 "전"에 트랜잭션 밖에서 끝나 있어야 한다 —
 * 호출부 서비스가 @Transactional을 갖고 있으면 그 안에서 LLM을 부르는 동안 DB 커넥션이 오래 점유되므로,
 * 각 서비스는 LLM 호출 후 결과 텍스트만 들고 이 Executor를 호출하는 방식으로 트랜잭션 경계를 분리한다.
 */
@Service
@RequiredArgsConstructor
public class AiDraftPersistenceExecutor {

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;

    @Transactional
    public AiGeneratedMessage saveDraftInTx(
            Store store, Account owner, AiMessageType type, String targetType, Long targetId,
            String title, String content, AiChannel channel,
            String actionLogTargetType, String actionDescription
    ) {
        AiGeneratedMessage message = aiGeneratedMessageRepository.save(
                AiGeneratedMessage.createDraft(store, owner, type, targetType, targetId, title, content, channel));
        aiActionLogRepository.save(AiActionLog.record(
                store, owner, AiActionType.DRAFT_CREATED, actionLogTargetType,
                message.getAiGeneratedMessageId(), actionDescription));
        return message;
    }
}
