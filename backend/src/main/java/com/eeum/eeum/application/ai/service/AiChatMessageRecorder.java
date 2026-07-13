package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import com.eeum.eeum.domain.ai.enums.AiChatRole;
import com.eeum.eeum.domain.ai.repository.AiChatMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챗봇 대화 기록(사용자/어시스턴트) 저장만 전담하는 짧은 트랜잭션.
 * LLM 호출(외부 HTTP)이 두 저장 사이에 끼어 있어도 DB 트랜잭션을 오래 붙잡지 않도록 분리했다.
 */
@Service
@RequiredArgsConstructor
public class AiChatMessageRecorder {

    private final AiChatMessageRepository aiChatMessageRepository;

    @Transactional
    public void record(Store store, Account account, AiChatRole role, String text) {
        aiChatMessageRepository.save(AiChatMessage.create(store, account, role, text));
    }
}
