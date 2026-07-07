package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiDraftCapacityExceededResponseDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.ai.repository.AiUsageLogRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.YearMonth;

/**
 * AI 매니저 공통 지원 서비스 — 사장/상점 소유 검증, 플랜 게이팅, 월 사용량 카운트.
 * 트랜잭션 경계는 호출하는 기능 서비스 쪽에 둔다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerSupportService {

    private static final Duration USAGE_LOCK_LEASE = Duration.ofSeconds(5);

    // 초안 보관 개수 캡 — AI 생성 횟수(월 사용량)와는 별개로 타입별 DRAFT 보관 개수를 제한
    private static final int DRAFT_LIMIT_PER_TYPE = 20;

    private final StoreRepository storeRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiPlanPolicy aiPlanPolicy;
    private final RedisLockService redisLockService;
    private final AiUsageRecorder aiUsageRecorder;

    // 로그인한 사장의 상점 조회 — 없으면 STORE_NOT_FOUND
    public Store getOwnerStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    // 활성 구독이 없으면 FREE — 미결제 사장이 유료 생성 기능을 쓰지 못하도록 안전한 기본값을 사용한다.
    // 개발/테스트 환경에서는 seed 데이터로 AiPlanSubscription(BASIC/PRO)을 넣어 사용한다.
    public AiPlanType getPlanType(Long storeId) {
        return aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(storeId)
                .map(AiPlanSubscription::getPlanType)
                .orElse(AiPlanType.FREE);
    }

    // 조회성 기능 — 플랜 게이팅만 검증
    public void validateFeature(Store store, AiFeature feature) {
        aiPlanPolicy.validateAccess(getPlanType(store.getStoreId()), feature);
    }

    // 생성성 기능 — 플랜 게이팅 + 분산 락 안에서 월 사용량 체크/기록 (동시 요청 초과 방지)
    // aiUsageRecorder.checkAndRecord 가 REQUIRES_NEW로 분리되어 락 안에서 커밋까지 완료됨 —
    // 락 해제 후 두 번째 요청이 커밋된 카운트를 정확히 읽는다.
    public void consumeGeneration(Store store, Long ownerAccountId, AiFeature feature, AiUsageType usageType) {
        AiPlanType plan = getPlanType(store.getStoreId());
        aiPlanPolicy.validateAccess(plan, feature);

        if (!aiPlanPolicy.isUsageCounted(feature)) {
            return;
        }

        String yearMonth = YearMonth.now().toString();
        redisLockService.executeWithLock(
                LockKeys.aiUsage(store.getStoreId()),
                USAGE_LOCK_LEASE,
                () -> aiUsageRecorder.checkAndRecord(store, ownerAccountId, plan, usageType, yearMonth)
        );
    }

    public long getMonthlyUsage(Long storeId) {
        return aiUsageLogRepository.countByStore_StoreIdAndYearMonth(storeId, YearMonth.now().toString());
    }

    // 초안 보관 개수 캡 — 타입별 DRAFT가 캡 이상이면 confirmDelete가 false일 때 확인을 요구하고,
    // true로 재요청하면 가장 오래된 DRAFT를 하드 삭제한 뒤 새 초안 저장을 진행시킨다.
    //
    // 동시성 주의(운영 전 점검 필요): 캡 조회 → 삭제 → 새 초안 저장이 하나의 락으로 묶여 있지 않다.
    // 같은 store+type에 빠른 연속 요청이 오면 캡을 살짝 넘기거나(카운트 미반영 상태에서 둘 다 통과)
    // 오래된 초안이 중복 삭제될 수 있다. 필요 시 store+type 단위 Redis 락
    // (예: LockKeys.aiDraftCapacity(storeId, type))으로 캡 검증~저장 구간을 감싸는 것을 권장한다.
    public void enforceDraftCapacity(Store store, AiMessageType type, boolean confirmDelete) {
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
        aiGeneratedMessageRepository
                .findFirstByStore_StoreIdAndTypeAndStatusOrderByCreatedAtAsc(store.getStoreId(), type, AiMessageStatus.DRAFT)
                .ifPresent(aiGeneratedMessageRepository::delete);
    }

    // 메시지 조회 + 소유자 검증 — 다른 사장의 메시지 접근 시 AI_FORBIDDEN
    public AiGeneratedMessage getOwnedMessage(Long accountId, Long messageId) {
        AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_MESSAGE_NOT_FOUND));
        if (!message.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.AI_FORBIDDEN);
        }
        return message;
    }
}
