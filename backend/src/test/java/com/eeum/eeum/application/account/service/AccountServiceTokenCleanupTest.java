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
    @Mock OwnerStoreWithdrawalService ownerStoreWithdrawalService;
    @Mock AccountMapper accountMapper;
    @Mock OwnerApplicationMapper ownerApplicationMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock FavoriteService favoriteService;

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
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
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
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
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
        when(account.isWithdrawn()).thenReturn(true);
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
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
        when(account.getRole()).thenReturn(AccountRole.ROLE_USER);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        // when
        accountService.withdraw(accountId, request);

        // then
        verify(account).withdraw();
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));
        verify(tokenService, never()).consumeReAuthToken(any());
        verify(tokenService, never()).deleteRefreshToken(any());
    }

    @Test
    void withdraw_성공_시_찜을_정리하고_그_전에_변경사항을_flush한다() {
        // given — 탈퇴자가 남긴 찜이 상점·게시글 favoriteCount에 계속 잡히면 안 된다
        Long accountId = 1L;
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
        when(account.getRole()).thenReturn(AccountRole.ROLE_USER);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        // when
        accountService.withdraw(accountId, request);

        // then: 찜 카운트 감소는 영속성 컨텍스트를 비우는 bulk UPDATE라,
        // 앞 단계 변경을 먼저 flush하지 않으면 탈퇴 상태가 유실된다.
        InOrder inOrder = inOrder(account, accountRepository, favoriteService);
        inOrder.verify(account).withdraw();
        inOrder.verify(accountRepository).flush();
        inOrder.verify(favoriteService).deleteAllByAccountId(accountId);
    }

    @Test
    void withdraw_탈퇴_계정_접근_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(true);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> accountService.withdraw(accountId, request))
                .isInstanceOf(BusinessException.class);

        // DB 롤백 시나리오: 계정은 ACTIVE, 토큰은 유지되어야 함
        verify(eventPublisher, never()).publishEvent(any());
        verify(account, never()).withdraw();
    }

    @Test
    void withdraw_ROLE_OWNER_계정_탈퇴_시_deactivateForWithdrawal_호출_후_이벤트_발행() {
        // given
        Long accountId = 5L;
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
        when(account.getRole()).thenReturn(AccountRole.ROLE_OWNER);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        // when
        accountService.withdraw(accountId, request);

        // then: deactivateForWithdrawal 먼저 호출, 그 다음 withdraw 및 이벤트 발행
        verify(ownerStoreWithdrawalService).deactivateForWithdrawal(accountId);
        verify(account).withdraw();
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.reAuthAndRefresh(accountId));
    }

    @Test
    void withdraw_ROLE_OWNER_deactivateForWithdrawal_예외_시_withdraw_미호출_이벤트_미발행() {
        // given
        Long accountId = 5L;
        WithdrawRequestDto request = mock(WithdrawRequestDto.class);
        when(request.getReAuthToken()).thenReturn("reauth");

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
        when(account.getRole()).thenReturn(AccountRole.ROLE_OWNER);
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        doThrow(new RuntimeException("상점 비활성화 실패"))
                .when(ownerStoreWithdrawalService).deactivateForWithdrawal(accountId);

        // when & then
        assertThatThrownBy(() -> accountService.withdraw(accountId, request))
                .isInstanceOf(RuntimeException.class);

        verify(account, never()).withdraw();
        verify(eventPublisher, never()).publishEvent(any());
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
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
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
