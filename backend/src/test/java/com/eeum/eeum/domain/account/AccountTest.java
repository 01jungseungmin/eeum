package com.eeum.eeum.domain.account;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    // ─────────────────── setPrimaryRegion ───────────────────

    @Test
    void setPrimaryRegion_accountRegionId를_primaryRegionId에_저장() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");
        Long accountRegionId = 42L;

        // when
        account.setPrimaryRegion(accountRegionId);

        // then
        assertThat(account.getPrimaryRegionId()).isEqualTo(accountRegionId);
    }

    // ─────────────────── clearPrimaryRegion ───────────────────

    @Test
    void clearPrimaryRegion_primaryRegionId를_null로_초기화() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");
        account.setPrimaryRegion(42L);

        // when
        account.clearPrimaryRegion();

        // then
        assertThat(account.getPrimaryRegionId()).isNull();
    }

    // ─────────────────── withdraw ───────────────────

    @Test
    void withdraw_호출_시_status가_WITHDRAWN으로_변경되고_deletedAt_설정() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");

        // when
        account.withdraw();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(account.getDeletedAt()).isNotNull();
        assertThat(account.isWithdrawn()).isTrue();
    }

    // ─────────────────── cancelWithdrawal ───────────────────

    @Test
    void cancelWithdrawal_호출_시_status가_ACTIVE로_변경되고_deletedAt이_null로_초기화() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");
        account.withdraw(); // 먼저 탈퇴 처리

        // when
        account.cancelWithdrawal();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getDeletedAt()).isNull();
        assertThat(account.isWithdrawn()).isFalse();
        assertThat(account.isActive()).isTrue();
    }

    // ─────────────────── suspend / activate ───────────────────

    @Test
    void suspend_호출_시_status가_SUSPENDED로_변경() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");

        // when
        account.suspend();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.isActive()).isFalse();
    }

    @Test
    void activate_호출_시_status가_ACTIVE로_변경되고_deletedAt이_null로_초기화() {
        // given
        Account account = Account.createUser("test@test.com", "encoded", "홍길동", "nick", "010-0000-0000");
        account.suspend();

        // when
        account.activate();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getDeletedAt()).isNull();
    }
}
