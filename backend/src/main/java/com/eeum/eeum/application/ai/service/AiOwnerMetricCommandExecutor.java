package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실측값 upsert Executor — @Transactional이 Redis 락 안에서 시작되도록 별도 Bean으로 분리.
 * AiOperationRiskService가 락을 먼저 잡고 이 Executor를 호출해 upsert + action log를
 * 단일 TX로 처리하고 커밋 후 락 해제 순서를 보장한다.
 * ownerAccountId를 파라미터로 받아 store.getAccount() lazy-load 의존성을 제거한다.
 */
@Service
@RequiredArgsConstructor
public class AiOwnerMetricCommandExecutor {

    private final AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void upsertMetricInTx(Store store, Long ownerAccountId, AiOwnerMetricInputRequestDto request) {
        aiOwnerMetricInputRepository
                .findByStore_StoreIdAndMetricTypeAndYearMonth(
                        store.getStoreId(), request.getMetricType(), request.getYearMonth())
                .ifPresentOrElse(
                        existing -> existing.updateValue(request.getValue()),
                        () -> aiOwnerMetricInputRepository.save(AiOwnerMetricInput.create(
                                store, request.getMetricType(), request.getValue(), request.getYearMonth())));
        Account owner = accountRepository.getReferenceById(ownerAccountId);
        aiActionLogRepository.save(AiActionLog.record(
                store, owner, AiActionType.OWNER_METRIC_INPUT,
                request.getMetricType().name(), null, "사장님 실측값 입력 (" + request.getYearMonth() + ")"));
    }
}
