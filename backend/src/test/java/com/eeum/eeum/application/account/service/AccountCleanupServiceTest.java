package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.store.service.SettlementAccountDeleteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 후 30일 경과 계정의 물리 삭제 테스트.
 * favorite.account_id는 NOT NULL FK라 남아 있으면 계정 삭제가 제약 위반으로 실패한다.
 */
@ExtendWith(MockitoExtension.class)
class AccountCleanupServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock private AccountRepository accountRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private SettlementAccountDeleteService settlementAccountDeleteService;
    @Mock private FavoriteService favoriteService;

    @InjectMocks
    private AccountCleanupService accountCleanupService;

    @Test
    void 개인정보를_지우고_계정_행은_남긴다() {
        // given — 주문·결제·신고 등 18개 테이블이 이 계정을 참조한다.
        // 행을 지우면 FK 제약에 걸리고, 주문·결제는 보존 의무가 있어 함께 지울 수도 없다.
        Account account = account();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        assertThat(account.isAnonymized()).isTrue();
        assertThat(account.getEmail()).doesNotContain("user@test.com");
        assertThat(account.getPhone()).isEmpty();
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void 익명화를_먼저_반영한_뒤_정리한다() {
        // given — 정리 작업에는 영속성 컨텍스트를 비우는 bulk 연산이 섞여 있다.
        // 익명화를 나중에 하면 엔티티가 분리된 뒤라 파기가 통째로 유실된다.
        Account account = account();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        InOrder inOrder = inOrder(accountRepository, favoriteService);
        inOrder.verify(accountRepository).flush();
        inOrder.verify(favoriteService).deleteAllByAccountId(ACCOUNT_ID);
    }

    @Test
    void 계정에_딸린_개인정보_행은_함께_지운다() {
        // given — GPS 활동지역, 사업자번호, 정산 계좌는 식별 정보라 남길 이유가 없다
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account()));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        verify(favoriteService).deleteAllByAccountId(ACCOUNT_ID);
        verify(accountRegionRepository).deleteByAccount_AccountId(ACCOUNT_ID);
        verify(ownerInfoRepository).deleteByAccount_AccountId(ACCOUNT_ID);
        verify(settlementAccountDeleteService).deleteByAccountId(ACCOUNT_ID);
    }

    @Test
    void 탈퇴가_취소된_계정은_파기하지_않는다() {
        // given — 대상 조회와 잠금 사이에 관리자가 탈퇴를 취소할 수 있다.
        // 그대로 진행하면 활성 계정의 개인정보를 파기하게 된다.
        Account account = account();
        account.cancelWithdrawal();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        assertThat(account.isAnonymized()).isFalse();
        verifyNoInteractions(favoriteService, settlementAccountDeleteService);
    }

    @Test
    void 유예_기간이_남은_계정은_파기하지_않는다() {
        // given — 탈퇴 직후 다시 탈퇴하면 deletedAt이 갱신된다. 임계값을 다시 확인해야 한다.
        Account account = account();
        ReflectionTestUtils.setField(account, "deletedAt", LocalDateTime.now().minusDays(1));
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        assertThat(account.isAnonymized()).isFalse();
    }

    @Test
    void 이미_파기한_계정은_다시_처리하지_않는다() {
        // given — 재처리하면 이미 익명화된 값 위에 또 덮어써 파기 시각이 계속 갱신된다
        Account account = account();
        account.anonymize();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        // then
        verifyNoInteractions(favoriteService, settlementAccountDeleteService, accountRegionRepository);
    }

    @Test
    void 이미_사라진_계정은_건너뛴다() {
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.empty());

        accountCleanupService.anonymizeAccount(ACCOUNT_ID);

        verifyNoInteractions(favoriteService, settlementAccountDeleteService, accountRegionRepository);
    }

    @Test
    void 대상_조회는_커서_다음부터_배치_크기만큼_ID만_읽는다() {
        // given — 엔티티 전체를 적재하면 backlog가 쌓였을 때 메모리와 잠금 보유 시간을 밀어낸다
        when(accountRepository.findAnonymizeTargetIdsAfter(any(), any(), any(), any()))
                .thenReturn(List.of(11L, 12L));

        // when
        List<Long> targets = accountCleanupService.findAnonymizeTargetIds(10L, 100);

        // then
        assertThat(targets).containsExactly(11L, 12L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(accountRepository).findAnonymizeTargetIdsAfter(
                eq(AccountStatus.WITHDRAWN), any(), eq(10L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    private Account account() {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "status", AccountStatus.WITHDRAWN);
        ReflectionTestUtils.setField(account, "deletedAt", LocalDateTime.now().minusDays(31));
        return account;
    }
}
