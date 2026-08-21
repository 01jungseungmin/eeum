package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.store.service.SettlementAccountDeleteService;
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
    private final SettlementAccountDeleteService settlementAccountDeleteService;
    private final FavoriteService favoriteService;

    // 탈퇴 후 30일이 지난 계정의 개인정보를 파기한다.
    // 대상 조회만 하고 파기는 계정별 트랜잭션으로 넘긴다 — 한 계정의 실패가 회차 전체를 되돌리면
    // 정상 처리 가능한 계정까지 함께 죽고, 대상이 쌓이며 매일 같은 실패를 반복한다.
    @Transactional(readOnly = true)
    public List<Long> findAnonymizeTargetIds() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        return accountRepository.findWithdrawnAccountsBefore(AccountStatus.WITHDRAWN, threshold)
                .stream()
                .map(Account::getAccountId)
                .toList();
    }

    /**
     * 계정 한 건의 개인정보를 파기한다.
     *
     * <p>계정 행은 남긴다 — 주문·결제·신고·채팅 등 18개 테이블이 이 계정을 참조하고,
     * 그중 주문·결제는 정산과 보존 의무가 걸려 지울 수 없다.
     * 파기 대상은 식별 정보이지 활동 이력이 아니다.
     *
     * <p>REQUIRES_NEW로 트랜잭션을 분리해 실패를 이 계정 하나로 가둔다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anonymizeAccount(Long accountId) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElse(null);

        if (account == null || account.isAnonymized()) {
            return;
        }

        // 익명화를 먼저 반영한다. 아래 정리 작업에는 영속성 컨텍스트를 비우는
        // bulk 연산(@Modifying(clearAutomatically))이 섞여 있어, 나중에 호출하면
        // 엔티티가 분리된 뒤라 dirty checking 대상에서 빠져 파기가 통째로 유실된다.
        account.anonymize();
        accountRepository.flush();

        // 탈퇴 시점에 이미 정리되지만, 이 배선 이전에 탈퇴한 계정은 찜이 남아 있다.
        // 남은 찜이 없으면 추가 쿼리 없이 끝나므로 무조건 호출해도 안전하다.
        favoriteService.deleteAllByAccountId(accountId);

        // 계정에 딸린 개인정보 행 삭제 — GPS 활동지역, 사업자 정보(사업자번호),
        // 정산 계좌(계좌번호·예금주). 활동 이력이 아니라 식별 정보다.
        accountRegionRepository.deleteByAccount_AccountId(accountId);
        ownerInfoRepository.deleteByAccount_AccountId(accountId);
        settlementAccountDeleteService.deleteByAccountId(accountId);

        log.info("탈퇴 후 30일 경과 계정 개인정보 파기 완료: accountId={}", accountId);
    }
}
