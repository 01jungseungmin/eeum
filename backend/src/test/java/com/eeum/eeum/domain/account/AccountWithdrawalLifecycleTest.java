package com.eeum.eeum.domain.account;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 탈퇴 → 유예 → 개인정보 파기 수명주기 테스트.
 * 파기는 되돌릴 수 없으므로 상태 전이의 순서와 차단 지점을 도메인에서 고정한다.
 */
class AccountWithdrawalLifecycleTest {

    @Test
    void 유예_기간_안에는_탈퇴를_취소할_수_있다() {
        Account account = withdrawnAccount();

        account.cancelWithdrawal();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getDeletedAt()).isNull();
    }

    @Test
    void 개인정보가_파기된_계정은_되살릴_수_없다() {
        // given — 되살리면 email·이름·전화가 지워진 채 ACTIVE가 되어
        // 로그인도 안 되고 남에게는 "탈퇴한 회원"으로 보이는 계정이 남는다
        Account account = withdrawnAccount();
        account.anonymize();

        // when & then
        assertThatThrownBy(account::cancelWithdrawal)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALREADY_ANONYMIZED);
    }

    @Test
    void 파기는_식별_정보만_지우고_상태는_탈퇴로_유지한다() {
        Account account = withdrawnAccount();

        account.anonymize();

        assertThat(account.isAnonymized()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(account.getEmail()).doesNotContain("user@test.com");
        assertThat(account.getNickname()).doesNotContain("사용자닉");
        assertThat(account.getPhone()).isEmpty();
    }

    @Test
    void 파기된_계정의_email과_nickname은_계정마다_다르다() {
        // given — UNIQUE 제약이 있어 고정값으로 지우면 두 번째 탈퇴자부터 충돌한다
        Account first = withdrawnAccount();
        ReflectionTestUtils.setField(first, "accountId", 1L);
        Account second = withdrawnAccount();
        ReflectionTestUtils.setField(second, "accountId", 2L);

        // when
        first.anonymize();
        second.anonymize();

        // then
        assertThat(first.getEmail()).isNotEqualTo(second.getEmail());
        assertThat(first.getNickname()).isNotEqualTo(second.getNickname());
    }

    private Account withdrawnAccount() {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", 1L);
        account.withdraw();
        return account;
    }
}
