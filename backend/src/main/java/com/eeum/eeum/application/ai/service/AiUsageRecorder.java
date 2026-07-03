package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.policy.AiPlanPolicy;
import com.eeum.eeum.domain.ai.entity.AiUsageLog;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiUsageLogRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 사용량 체크+기록을 REQUIRES_NEW로 즉시 커밋.
 * 같은 클래스 내 self-invocation으로는 Spring AOP 프록시가 동작하지 않으므로 별도 빈으로 분리.
 * 락 안에서 커밋이 완료되기 때문에 락 해제 후 두 번째 요청이 정확한 카운트를 읽는다.
 * 트레이드오프: 이후 본 작업(텍스트 생성 등)이 실패해도 사용량 1회가 소진된다 — "생성 시도 = 소진" 정책 기준.
 */
@Service
@RequiredArgsConstructor
public class AiUsageRecorder {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiPlanPolicy aiPlanPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecord(Store store, AiPlanType plan, AiUsageType usageType, String yearMonth) {
        Integer limit = aiPlanPolicy.monthlyLimit(plan);
        if (limit != null) {
            if (limit == 0) {
                throw new BusinessException(ErrorCode.AI_PLAN_REQUIRED);
            }
            long used = aiUsageLogRepository.countByStore_StoreIdAndYearMonth(
                    store.getStoreId(), yearMonth);
            if (used >= limit) {
                throw new BusinessException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED);
            }
        }
        aiUsageLogRepository.save(AiUsageLog.record(store, store.getAccount(), usageType, yearMonth));
    }
}
