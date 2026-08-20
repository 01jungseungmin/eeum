package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.mapper.AccountMapper;
import com.eeum.eeum.application.account.mapper.OwnerApplicationMapper;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTokenCleanupTest {

    @InjectMocks AccountService accountService;

    @Mock AccountRepository accountRepository;
    @Mock OwnerInfoRepository ownerInfoRepository;
    @Mock AccountRegionRepository accountRegionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;
    @Mock AccountMapper accountMapper;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AccountWithdrawalProcessor accountWithdrawalProcessor;

    // ─────────────────── changePassword ───────────────────

    @Test
    void changePassword_성공_시_이벤트로_reAuth_refresh_토큰_정리() {
        // given
        Long accountId = 1L;
        String reAuthToken = "reauth-token";
        String currentPass = "current";
        String newPass = "NewPass123!";

        ChangePasswordRequestDto request = mock(ChangePasswordRequestDto.class);
        when(request.getReAuthToken()).thenReturn(reAuthToken);
        when(request.getCurrentPassword()).thenReturn(currentPass);
        when(request.getNewPassword()).thenReturn(newPass);

        Account account = mock(Account.class);
        when(account.isOAuthAccount()).thenReturn(false);
        when(account.getPassword()).thenReturn("encodedCurrent");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(currentPass, "encodedCurrent")).thenReturn(true);
        when(passwordEncoder.encode(newPass)).thenReturn("encodedNew");

        // when
        accountService.changePassword(accountId, request);

        // then: tokenService 직접 호출 없이 이벤트만 발행
        verify(account).changePassword("encodedNew");
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));
        verify(tokenService, never()).consumeReAuthToken(any());
        verify(tokenService, never()).deleteRefreshToken(any());
    }

    @Test
    void changePassword_현재_비밀번호_불일치_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        ChangePasswordRequestDto request = mock(ChangePasswordRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");
        when(request.getCurrentPassword()).thenReturn("wrong");
        // getNewPassword()는 비밀번호 불일치 분기에서 호출되지 않음 — stub 불필요

        Account account = mock(Account.class);
        when(account.isOAuthAccount()).thenReturn(false);
        when(account.getPassword()).thenReturn("encoded");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> accountService.changePassword(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_PASSWORD);

        // DB 변경 없이 예외 발생 → 이벤트 미발행 → 토큰 유지
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changePassword_탈퇴_계정_접근_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        ChangePasswordRequestDto request = mock(ChangePasswordRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        doThrow(new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN)).when(account).assertWritable();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> accountService.changePassword(accountId, request))
                .isInstanceOf(BusinessException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── withdraw ───────────────────

    @Test
    void withdraw_성공_시_이벤트로_reAuth_refresh_토큰_정리() {
        // given
        Long accountId = 1L;
        Account account = givenActiveAccount(accountId);

        // when
        accountService.withdraw(accountId, withdrawRequest());

        // then
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));
        verify(tokenService, never()).consumeReAuthToken(any());
        verify(tokenService, never()).deleteRefreshToken(any());
    }

    @Test
    void withdraw는_공통_탈퇴_절차에_위임하고_그_뒤에_토큰을_정리한다() {
        // given — 상점 비활성화·탈퇴 처리·찜 정리는 관리자 강제 탈퇴와 같은 절차를 써야 한다.
        // 두 경로에 따로 적어두면 한쪽만 고쳐져 찜 카운트가 남는 식으로 어긋난다.
        Long accountId = 1L;
        Account account = givenActiveAccount(accountId);

        // when
        accountService.withdraw(accountId, withdrawRequest());

        // then — 토큰 정리 이벤트는 뒷정리가 끝난 뒤에 발행돼야 롤백 시 토큰이 살아남는다
        InOrder inOrder = inOrder(accountWithdrawalProcessor, eventPublisher);
        inOrder.verify(accountWithdrawalProcessor).process(account);
        inOrder.verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));
    }

    @Test
    void withdraw_탈퇴_계정_접근_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        Account account = mock(Account.class);
        doThrow(new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN)).when(account).assertWritable();
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> accountService.withdraw(accountId, withdrawRequest()))
                .isInstanceOf(BusinessException.class);

        // DB 롤백 시나리오: 계정은 ACTIVE, 토큰은 유지되어야 함
        verify(eventPublisher, never()).publishEvent(any());
        verify(accountWithdrawalProcessor, never()).process(any());
    }

    @Test
    void withdraw_뒷정리_실패_시_이벤트_미발행() {
        // given
        Long accountId = 5L;
        Account account = givenActiveAccount(accountId);
        doThrow(new RuntimeException("상점 비활성화 실패"))
                .when(accountWithdrawalProcessor).process(account);

        // when & then
        assertThatThrownBy(() -> accountService.withdraw(accountId, withdrawRequest()))
                .isInstanceOf(RuntimeException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private Account givenActiveAccount(Long accountId) {
        Account account = mock(Account.class);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        return account;
    }

    private WithdrawRequestDto withdrawRequest() {
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");
        return request;
    }

    // ─────────────────── changePassword - reAuthToken 실패 guard ───────────────────

    @Test
    void changePassword_reAuthToken_검증_실패_시_계정_조회_미호출_이벤트_미발행() {
        // given
        Long accountId = 1L;
        ChangePasswordRequestDto request = mock(ChangePasswordRequestDto.class);
        when(request.getReAuthToken()).thenReturn("invalid-reauth");

        doThrow(new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN))
                .when(tokenService).validateReAuthToken(accountId, "invalid-reauth");

        // when & then
        assertThatThrownBy(() -> accountService.changePassword(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_REAUTH_TOKEN);

        verify(accountRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changePassword_OAuth_계정_접근_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        ChangePasswordRequestDto request = mock(ChangePasswordRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isOAuthAccount()).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> accountService.changePassword(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_PASSWORD);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── withdraw - reAuthToken 실패 guard ───────────────────

    @Test
    void withdraw_reAuthToken_검증_실패_시_계정_조회_미호출_이벤트_미발행() {
        // given
        Long accountId = 1L;
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("invalid-reauth");

        doThrow(new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN))
                .when(tokenService).validateReAuthToken(accountId, "invalid-reauth");

        // when & then
        assertThatThrownBy(() -> accountService.withdraw(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_REAUTH_TOKEN);

        verify(accountRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
