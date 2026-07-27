package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiDraftCapacityExceededResponseDto;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초안 보관 캡(타입별 20개) — 사전 검증(퇴거 없음)과 저장 성공 후 초과분 자기치유(퇴거)를 분리한다.
 *
 * 과거에는 "캡 검증 시점에 즉시 퇴거 후 새 초안 저장"이었는데, 이 둘이 서로 다른 트랜잭션(퇴거는 락+
 * REQUIRES_NEW로 즉시 커밋, 저장은 호출부 서비스의 별도 트랜잭션)에 걸쳐 있어 두 가지 문제가 있었다.
 * 1) 캡 검증~저장 사이에 락이 없어 동시 요청 시 캡을 살짝 넘길 수 있었다.
 * 2) 퇴거는 이미 커밋됐는데 그 뒤 AI 생성(외부 API)이나 저장이 실패하면, 가장 오래된 초안만 사라지고
 *    새 초안은 생기지 않는 사용자 데이터 손실이 발생할 수 있었다.
 * 지금은 validate()가 퇴거 없이 사전 확인만 하고(불필요한 LLM 호출도 막는다), 새 초안 저장이 성공한
 * "뒤"에 evictExcessInTx()를 호출해 그 시점 기준으로 초과분만 퇴거한다 — 저장 실패 시 아무것도 지워지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiDraftCapacityRecorder {

    private static final int DRAFT_LIMIT_PER_TYPE = 20;

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;

    // 캡 초과 && 미확인이면 409 — 퇴거는 하지 않는다 (LLM 호출 전에 걸러 비용 낭비를 막는 것이 목적)
    @Transactional(readOnly = true)
    public void validate(Store store, AiMessageType type, boolean confirmDelete) {
        long draftCount = aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                store.getStoreId(), type, AiMessageStatus.DRAFT);
        if (draftCount < DRAFT_LIMIT_PER_TYPE) {
            return;
        }
        if (!confirmDelete) {
            throw new BusinessException(ErrorCode.AI_DRAFT_LIMIT_EXCEEDED,
                    AiDraftCapacityExceededResponseDto.builder()
                            .type(type)
                            .limit(DRAFT_LIMIT_PER_TYPE)
                            .currentCount(draftCount)
                            .deletePolicy("OLDEST_DRAFT")
                            .build());
        }
        // confirmDelete=true — 사용자가 이미 삭제에 동의했으므로 여기서는 통과시키고,
        // 실제 퇴거는 새 초안 저장이 성공한 뒤 evictExcessInTx가 처리한다.
    }

    // 새 초안 저장 성공 "후"에만 호출 — 그 시점 기준으로 캡을 초과한 만큼만 가장 오래된 것부터 퇴거한다.
    // 락 안에서 커밋까지 마치므로 동시 저장으로 캡이 넘거나 같은 초안이 중복 퇴거되지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evictExcessInTx(Store store, AiMessageType type) {
        long draftCount = aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatus(
                store.getStoreId(), type, AiMessageStatus.DRAFT);
        long excess = draftCount - DRAFT_LIMIT_PER_TYPE;
        for (long i = 0; i < excess; i++) {
            aiGeneratedMessageRepository
                    .findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(store.getStoreId(), type, AiMessageStatus.DRAFT)
                    .ifPresent(aiGeneratedMessageRepository::delete);
        }
    }
}
