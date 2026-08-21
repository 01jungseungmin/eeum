package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사용자 쓰기 경로의 계정 가드 테스트.
 * 상태별로 다른 코드를 던져야 한다 — 하나로 뭉치면 가입 미완료 계정이 "정지된 계정"으로 응답된다.
 */
@ExtendWith(MockitoExtension.class)
class AccountWriteGuardTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private AccountWriteGuard accountWriteGuard;

    @Test
    void 활성_계정은_잠금_조회로_반환한다() {
        // given — 잠그지 않으면 탈퇴 정리와 겹쳐 데이터가 되살아나거나 카운터가 어긋난다
        Account active = account(AccountStatus.ACTIVE);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(active));

        // when
        Account result = accountWriteGuard.lockActive(ACCOUNT_ID);

        // then
        assertThat(result).isSameAs(active);
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void 탈퇴_계정은_탈퇴로_거절한다() {
        assertThatThrownBy(() -> lockWith(AccountStatus.WITHDRAWN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    void 정지_계정은_정지로_거절한다() {
        assertThatThrownBy(() -> lockWith(AccountStatus.SUSPENDED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void 가입_미완료_계정은_정지가_아니라_가입_미완료로_거절한다() {
        assertThatThrownBy(() -> lockWith(AccountStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SIGNUP_INCOMPLETE);
    }

    @Test
    void 없는_계정은_찾을_수_없음으로_거절한다() {
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountWriteGuard.lockActive(ACCOUNT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    private void lockWith(AccountStatus status) {
        when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                .thenReturn(Optional.of(account(status)));
        accountWriteGuard.lockActive(ACCOUNT_ID);
    }

    private Account account(AccountStatus status) {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "사용자", "사용자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }
}
