package com.eeum.eeum.application.store.service;

import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.SettlementAccountRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 파기.
 *
 * <p>계좌번호·예금주는 개인정보다. 상점 자체는 주문·정산 이력이 매달려 있어 남기지만,
 * 계좌 정보는 탈퇴 후 보관할 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAccountDeleteService {

    private final StoreRepository storeRepository;
    private final SettlementAccountRepository settlementAccountRepository;

    @Transactional
    public void deleteByAccountId(Long accountId) {
        storeRepository.findByAccount_AccountId(accountId)
                .map(Store::getStoreId)
                .ifPresent(storeId -> {
                    settlementAccountRepository.deleteByStore_StoreId(storeId);
                    log.info("정산 계좌 파기: accountId={}, storeId={}", accountId, storeId);
                });
    }
}
