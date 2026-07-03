package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
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
        return aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(storeId)
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
    public void consumeGeneration(Store store, AiFeature feature, AiUsageType usageType) {
        AiPlanType plan = getPlanType(store.getStoreId());
        aiPlanPolicy.validateAccess(plan, feature);

        if (!aiPlanPolicy.isUsageCounted(feature)) {
            return;
        }

        String yearMonth = YearMonth.now().toString();
        redisLockService.executeWithLock(
                LockKeys.aiUsage(store.getStoreId()),
                USAGE_LOCK_LEASE,
                () -> aiUsageRecorder.checkAndRecord(store, plan, usageType, yearMonth)
        );
    }

    public long getMonthlyUsage(Long storeId) {
        return aiUsageLogRepository.countByStore_StoreIdAndYearMonth(storeId, YearMonth.now().toString());
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
