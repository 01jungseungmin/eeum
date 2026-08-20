package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.store.service.StorePhysicalDeleteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final FavoriteService favoriteService;

    // 탈퇴 후 30일이 지난 계정을 물리 삭제.
    // 대상 조회만 하고 삭제는 계정별 트랜잭션으로 넘긴다 — 한 계정의 실패가 회차 전체를 되돌리면
    // 정상 삭제 가능한 계정까지 함께 죽고, 대상이 쌓이며 매일 같은 실패를 반복한다.
    @Transactional(readOnly = true)
    public List<Long> findDeletableAccountIds() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        return accountRepository.findWithdrawnAccountsBefore(AccountStatus.WITHDRAWN, threshold)
                .stream()
                .map(Account::getAccountId)
                .toList();
    }

    // 계정 한 건을 하위 데이터까지 물리 삭제한다.
    // REQUIRES_NEW로 트랜잭션을 분리해 실패를 이 계정 하나로 가둔다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElse(null);

        if (account == null) {
            return;
        }

        // 찜 정리 — favorite.account_id는 NOT NULL FK라 남아 있으면 계정 물리 삭제가 제약 위반으로 실패한다.
        // 탈퇴 시점(AccountService.withdraw)에 이미 정리되지만, 이 배선 이전에 탈퇴한 계정은 찜이 남아 있다.
        // 남은 찜이 없으면 추가 쿼리 없이 끝나므로 무조건 호출해도 안전하다.
        favoriteService.deleteAllByAccountId(accountId);

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
