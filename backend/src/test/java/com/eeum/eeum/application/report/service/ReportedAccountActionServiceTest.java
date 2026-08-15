package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportedAccountActionServiceTest {

    @InjectMocks private ReportedAccountActionService service;

    @Mock private AccountRepository accountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long ACCOUNT_ID = 20L;

    @Test
    void 작성자_경고는_계정_존재와_제재_가능_여부를_확인한다() {
        // given
        Account account = createAccount();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        Long result = service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID);

        // then
        assertThat(result).isEqualTo(ACCOUNT_ID);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository, never()).findByIdWithLock(ACCOUNT_ID);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 작성자_정지는_계정을_잠금하고_토큰_정리_이벤트를_발행한다() {
        // given
        Account account = createAccount();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        Long result = service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID);

        // then
        assertThat(result).isEqualTo(ACCOUNT_ID);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        verify(accountRepository, never()).findById(ACCOUNT_ID);
        verify(eventPublisher).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 경고_대상_계정이_없으면_조치할_수_없다() {
        // given
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
    }

    @Test
    void 정지_대상_계정이_없으면_조치할_수_없다() {
        // given
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 관리자_계정은_제재할_수_없다() {
        // given
        Account account = createAccount();
        ReflectionTestUtils.setField(account, "role", AccountRole.ROLE_ADMIN);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 탈퇴한_계정은_경고할_수_없다() {
        // given
        Account account = createAccount();
        account.withdraw();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    void 계정_조치가_아닌_액션은_거부한다() {
        // when & then
        assertThatThrownBy(() -> service.apply(ReportAction.HIDE_POST, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        verify(accountRepository, never()).findById(ACCOUNT_ID);
        verify(accountRepository, never()).findByIdWithLock(ACCOUNT_ID);
    }

    private Account createAccount() {
        Account account = Account.createUser(
                "author@test.com", "encoded", "작성자", "작성자닉", "010-1111-2222");
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        return account;
    }
}
