package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.store.service.StorePhysicalDeleteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCleanupService {

    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StorePhysicalDeleteService storePhysicalDeleteService;

    // 탈퇴 후 30일이 지난 계정을 물리 삭제
    @Transactional
    public void deleteWithdrawnAccountsAfter30Days() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        List<Account> accounts = accountRepository.findWithdrawnAccountsBefore(
                AccountStatus.WITHDRAWN,
                threshold
        );

        if (accounts.isEmpty()) {
            log.info("영구 삭제 대상 탈퇴 계정 없음");
            return;
        }

        for (Account account : accounts) {
            Long accountId = account.getAccountId();

            // 상점 하위 데이터 먼저 삭제 — ownerSignup은 승인 전(ROLE_USER)에도 Store를 생성하므로
            // role로 게이팅하면 미승인 사장의 Store가 FK 제약 위반/고아로 남는다. deleteStoreDataByAccountId는
            // 상점이 없으면 즉시 return하므로 무조건 호출해도 안전하다.
            storePhysicalDeleteService.deleteStoreDataByAccountId(accountId);

            // 계정 하위 데이터 삭제
            accountRegionRepository.deleteByAccount_AccountId(accountId);
            ownerInfoRepository.deleteByAccount_AccountId(accountId);

            // 계정 삭제
            accountRepository.delete(account);

            log.info("탈퇴 후 30일 경과 계정 영구 삭제 완료: accountId={}", accountId);
        }
    }
}