package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.used.service.UsedProductWithdrawalService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 탈퇴 처리의 공통 절차.
 * <p>
 * 본인 탈퇴({@code AccountService.withdraw})와 관리자 강제 탈퇴
 * ({@code AdminAccountService.forceDeleteAccount})가 같은 뒷정리를 해야 한다.
 * 두 곳에 따로 적어두면 한쪽만 고쳐져 찜 카운트가 남는 식으로 어긋난다.
 * <p>
 * 호출 전제: 대상 Account 행을 이미 잠근 상태여야 한다(잠금 순서 account → store → favorite).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountWithdrawalProcessor {

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    private final FavoriteService favoriteService;
    private final UsedProductWithdrawalService usedProductWithdrawalService;

    public void process(Account account) {
        Long accountId = account.getAccountId();

        // 1. 이 트랜잭션이 잠글 상점 행을 ID 오름차순으로 미리 확보한다.
        // 서로의 상점을 찜한 두 사장이 동시에 탈퇴하면, 각자 자기 상점을 잡고 상대 상점을
        // 기다리는 순환 교착이 난다(비활성화는 자기 상점, 찜 정리는 찜한 상점을 잠근다).
        lockStoresInIdOrder(accountId);

        // 2. 사장 계정이면 상점/상품/이벤트 상품 비활성화 — 탈퇴한 사장의 상점이
        // 사용자 화면에 계속 노출되고 주문·예약이 들어오는 것을 막는다.
        if (account.getRole() == AccountRole.ROLE_OWNER) {
            ownerStoreWithdrawalService.deactivateForWithdrawal(accountId);
        }

        // 3. 예약 중인 중고 거래 정리 — 상대가 기다리는 거래를 말없이 증발시키지 않는다.
        // 사용자 삭제 경로가 RESERVED 삭제를 막는 것과 같은 이유다(UsedProductService.delete).
        // 탈퇴 처리 앞에 둔다: 뒤에 두면 seller가 이미 비활성이라 게시글이 조회에서 걸러진다.
        usedProductWithdrawalService.cancelReservationsForWithdrawal(accountId);

        // 4. 탈퇴 처리
        account.withdraw();

        // 5. 찜 정리 — 탈퇴자가 남긴 찜이 상점·게시글의 favoriteCount에 계속 잡히면 안 된다.
        // 찜 카운트 감소는 영속성 컨텍스트를 비우는 bulk UPDATE(@Modifying(clearAutomatically))라
        // 앞 단계의 변경(탈퇴 상태, 사장 상점 비활성화)을 먼저 flush하지 않으면 그대로 유실된다.
        accountRepository.flush();
        favoriteService.deleteAllByAccountId(accountId);
    }

    // 탈퇴 트랜잭션이 건드릴 상점 행(자기 상점 + 찜한 상점)을 ID 오름차순으로 잠근다.
    private void lockStoresInIdOrder(Long accountId) {
        List<Long> storeIds = new ArrayList<>(
                favoriteService.findFavoriteRefIds(accountId, FavoriteRefType.STORE));

        storeRepository.findByAccount_AccountId(accountId)
                .map(Store::getStoreId)
                .filter(ownStoreId -> !storeIds.contains(ownStoreId))
                .ifPresent(storeIds::add);

        storeIds.stream().sorted().forEach(storeRepository::findByIdWithPessimisticLock);
    }
}
