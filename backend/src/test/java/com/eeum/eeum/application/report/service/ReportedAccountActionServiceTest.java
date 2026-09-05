package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.account.service.AccountSanctionPolicy;
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
import org.mockito.Spy;
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
    @Mock private com.eeum.eeum.application.used.service.UsedProductWithdrawalService usedProductWithdrawalService;

    // 제재 자격 판정은 이 서비스가 지켜야 하는 계약 그 자체다.
    // Mock으로 두면 관리자·탈퇴·중복 정지 차단이 전부 무력화된 채 통과한다.
    @Spy private AccountSanctionPolicy accountSanctionPolicy = new AccountSanctionPolicy();

    private static final Long ACCOUNT_ID = 20L;

    @Test
    void 작성자_경고는_계정_존재와_제재_가능_여부를_확인한다() {
        // given
        Account account = createAccount();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        Long result = service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID);

        // then
        assertThat(result).isEqualTo(ACCOUNT_ID);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        // 경고도 정지와 같은 잠금을 쓴다 — 대상 확인과 조치 사이에 탈퇴·정지가 끼어들면
        // 이미 사라진 계정에 경고가 기록된다. 상태는 바꾸지 않으므로 토큰 정리는 없다.
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
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
        // 직접 정지 API와 같은 범위로 회수해야 한다 — 한쪽만 Refresh만 지우면 경로에 따라 구멍이 생긴다
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.allTokens(ACCOUNT_ID));
    }

    @Test
    void 작성자_정지는_예약_중인_중고_거래도_정리한다() {
        // given — 정지되면 isPubliclyVisible()이 거짓이라 게시글이 전 화면에서 사라진다.
        // 직접 정지 API와 같은 정리를 하지 않으면 경로에 따라 예약이 남는 구멍이 생긴다.
        Account account = createAccount();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID);

        // then
        verify(usedProductWithdrawalService).cancelReservationsForSellerInactivation(ACCOUNT_ID);
    }

    @Test
    void 작성자_경고는_예약을_건드리지_않는다() {
        // given — 경고는 계정을 비활성으로 만들지 않는다. 게시글도 그대로 보인다.
        Account account = createAccount();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID);

        // then
        verify(usedProductWithdrawalService, never())
                .cancelReservationsForSellerInactivation(ACCOUNT_ID);
    }

    @Test
    void 경고_대상_계정이_없으면_조치할_수_없다() {
        // given
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.empty());

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

        // when & then: 관리자 직접 제재 API와 같은 오류로 통일한다 —
        // 같은 금지 사유가 경로마다 다른 코드로 나오면 클라이언트가 두 번 분기해야 한다.
        assertThatThrownBy(() -> service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ADMIN_SANCTION_NOT_ALLOWED);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 이미_정지된_계정에_정지_조치를_반복하면_거부하고_토큰_정리를_다시_발행하지_않는다() {
        // given: 같은 사용자에 대한 신고가 여러 건 접수돼 각각 SUSPEND_AUTHOR로 처리되는 상황
        Account account = createAccount();
        account.suspend();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when & then: 직접 정지 API와 같은 계약이어야 한다
        assertThatThrownBy(() -> service.apply(ReportAction.SUSPEND_AUTHOR, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALREADY_SUSPENDED);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 이미_정지된_계정에도_경고는_기록할_수_있다() {
        // given: 경고는 상태를 바꾸지 않으므로 정지 중에도 이력을 남길 수 있어야 한다.
        Account account = createAccount();
        account.suspend();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // when
        Long result = service.apply(ReportAction.WARN_AUTHOR, ACCOUNT_ID);

        // then
        assertThat(result).isEqualTo(ACCOUNT_ID);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        verify(eventPublisher, never()).publishEvent(isA(AccountTokenCleanupEvent.class));
    }

    @Test
    void 탈퇴한_계정은_경고할_수_없다() {
        // given
        Account account = createAccount();
        account.withdraw();
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(account));

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
