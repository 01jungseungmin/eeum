package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.PasswordNewRequestDto;
import com.eeum.eeum.application.auth.dto.request.PasswordResetRequestDto;
import com.eeum.eeum.application.auth.dto.request.ReissueRequestDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.eeum.eeum.application.auth.dto.request.ReAuthRequestDto;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTokenCleanupTest {

    @InjectMocks AuthService authService;

    @Mock AccountRepository accountRepository;
    @Mock TokenService tokenService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock RedisLockService redisLockService;
    @Mock ApplicationEventPublisher eventPublisher;

    // 나머지 의존성 — 이 테스트에서 호출되지 않으므로 Mock만 선언
    @Mock com.eeum.eeum.domain.account.repository.OwnerInfoRepository ownerInfoRepository;
    @Mock com.eeum.eeum.domain.store.repository.StoreRepository storeRepository;
    @Mock EmailService emailService;
    @Mock OAuthService oAuthService;
    @Mock org.springframework.data.redis.core.RedisTemplate redisTemplate;
    @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock BusinessVerificationService businessVerificationService;
    @Mock com.eeum.eeum.application.product.service.ProductCategoryService productCategoryService;
    @Mock com.eeum.eeum.common.service.RateLimitService rateLimitService;

    // ─────────────────── resetPassword ───────────────────

    @Test
    void resetPassword_성공_시_DB_커밋_후_토큰_정리_이벤트_발행() {
        // given
        Long accountId = 1L;
        String resetToken = "reset-token";
        String newPassword = "NewPass123!";

        PasswordNewRequestDto request = mock(PasswordNewRequestDto.class);
        when(request.getNewPassword()).thenReturn(newPassword);
        when(request.getNewPasswordConfirm()).thenReturn(newPassword);
        when(request.getPasswordResetToken()).thenReturn(resetToken);

        when(tokenService.consumePasswordResetToken(resetToken)).thenReturn(accountId);

        Account account = mock(Account.class);
        when(account.isOAuthAccount()).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded");

        // when
        authService.resetPassword(request);

        // then: tokenService 직접 호출 없이 이벤트만 발행
        verify(account).changePassword("encoded");
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.refreshOnly(accountId));
        verify(tokenService, never()).deleteRefreshToken(any());
        verify(tokenService, never()).deletePasswordResetToken(any());
    }

    @Test
    void resetPassword_계정_조회_실패_시_이벤트_미발행() {
        // given
        PasswordNewRequestDto request = mock(PasswordNewRequestDto.class);
        when(request.getNewPassword()).thenReturn("pass");
        when(request.getNewPasswordConfirm()).thenReturn("pass");
        when(request.getPasswordResetToken()).thenReturn("token");
        when(tokenService.consumePasswordResetToken("token")).thenReturn(99L);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        // DB 예외 발생 시 이벤트 미발행 → Redis 토큰이 유지되어 재시도 가능
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPassword_OAuth_계정_접근_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        PasswordNewRequestDto request = mock(PasswordNewRequestDto.class);
        when(request.getNewPassword()).thenReturn("pass");
        when(request.getNewPasswordConfirm()).thenReturn("pass");
        when(request.getPasswordResetToken()).thenReturn("token");
        when(tokenService.consumePasswordResetToken("token")).thenReturn(accountId);

        Account account = mock(Account.class);
        when(account.isOAuthAccount()).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── 비밀번호 재설정 계정 상태 ───────────────────
    // 정지·탈퇴 계정은 재설정해도 로그인할 수 없고, 탈퇴 취소는 관리자만 할 수 있어
    // 비밀번호를 되찾아야 할 이유가 없다. 상태를 보지 않으면 익명화 전(30일) 탈퇴자 수신함으로
    // 재설정 메일이 실제로 도착한다.

    private Account realAccount() {
        Account account = Account.createUser(
                "user@test.com", "encoded-pw", "홍길동", "nick", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", 1L);
        return account;
    }

    private PasswordNewRequestDto resetRequest() {
        PasswordNewRequestDto request = mock(PasswordNewRequestDto.class);
        when(request.getNewPassword()).thenReturn("NewPass123!");
        when(request.getNewPasswordConfirm()).thenReturn("NewPass123!");
        when(request.getPasswordResetToken()).thenReturn("token");
        return request;
    }

    @Test
    void resetPassword_정지된_계정은_비밀번호가_변경되지_않는다() {
        // given
        Account account = realAccount();
        account.suspend();
        when(tokenService.consumePasswordResetToken("token")).thenReturn(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> authService.resetPassword(resetRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(passwordEncoder, never()).encode(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPassword_탈퇴한_계정은_비밀번호가_변경되지_않는다() {
        // given: 발송 시점에 활성이었어도 토큰 유효 시간 안에 탈퇴할 수 있어 여기서 다시 본다
        Account account = realAccount();
        account.withdraw();
        when(tokenService.consumePasswordResetToken("token")).thenReturn(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> authService.resetPassword(resetRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(passwordEncoder, never()).encode(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sendPasswordResetEmail_정지된_계정에는_메일을_보내지_않는다() {
        // given
        Account account = realAccount();
        account.suspend();
        PasswordResetRequestDto request = mock(PasswordResetRequestDto.class);
        when(request.getEmail()).thenReturn("user@test.com");
        when(accountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> authService.sendPasswordResetEmail(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(emailService, never()).sendPasswordResetEmail(anyString());
    }

    @Test
    void sendPasswordResetEmail_탈퇴한_계정에는_메일을_보내지_않는다() {
        // given: 익명화(30일) 전이면 email이 그대로 남아 있어 실제 수신함으로 발송된다
        Account account = realAccount();
        account.withdraw();
        PasswordResetRequestDto request = mock(PasswordResetRequestDto.class);
        when(request.getEmail()).thenReturn("user@test.com");
        when(accountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> authService.sendPasswordResetEmail(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(emailService, never()).sendPasswordResetEmail(anyString());
    }

    // ─────────────────── reissue (Redis Lock) ───────────────────

    @Test
    void reissue_Redis_Lock으로_validate와_신규_토큰_저장이_원자적으로_실행됨() {
        // given
        Long accountId = 1L;
        String refreshToken = "valid-refresh-token";
        ReissueRequestDto request = mock(ReissueRequestDto.class);
        when(request.getRefreshToken()).thenReturn(refreshToken);

        when(jwtProvider.isValid(refreshToken)).thenReturn(true);
        when(jwtProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtProvider.getAccountId(refreshToken)).thenReturn(accountId);

        // lock이 실제로 supplier를 실행하도록 설정 (Supplier 오버로드 명시)
        when(redisLockService.<com.eeum.eeum.application.auth.dto.response.TokenResponseDto>executeWithLock(
                eq(LockKeys.reissue(accountId)), any(Duration.class), any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(2);
                    return supplier.get();
                });

        Account account = mock(Account.class);
        when(account.isWithdrawn()).thenReturn(false);
        when(account.isActive()).thenReturn(true);
        when(account.getAccountId()).thenReturn(accountId);
        when(account.getRole()).thenReturn(com.eeum.eeum.domain.account.enums.AccountRole.ROLE_USER);
        when(tokenService.validateRefreshToken(refreshToken)).thenReturn(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(jwtProvider.generateAccessToken(any(), any())).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("new-refresh");

        // when
        authService.reissue(request);

        // then: lock key로 직렬화됨
        verify(redisLockService).executeWithLock(
                eq(LockKeys.reissue(accountId)),
                any(Duration.class),
                any(Supplier.class)
        );
        verify(tokenService).validateRefreshToken(refreshToken);
        verify(tokenService).saveRefreshToken(eq(accountId), anyString());
    }

    @Test
    void reissue_JWT_형식_오류_시_lock_미획득() {
        // given
        ReissueRequestDto request = mock(ReissueRequestDto.class);
        when(request.getRefreshToken()).thenReturn("invalid-token");
        when(jwtProvider.isValid("invalid-token")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(redisLockService, never()).executeWithLock(any(), any(Duration.class), any(Supplier.class));
    }

    // ─────────────────── logout (Redis Lock) ───────────────────

    @Test
    void logout_성공_시_reissue와_동일한_lock_키로_직렬화() {
        // given
        Long accountId = 1L;
        String refreshToken = "valid-refresh";
        String accessToken = "valid-access";
        String authHeader = "Bearer " + accessToken;

        ReissueRequestDto request = mock(ReissueRequestDto.class);
        when(request.getRefreshToken()).thenReturn(refreshToken);

        when(jwtProvider.resolveAccessToken(authHeader)).thenReturn(accessToken);
        when(tokenService.validateRefreshToken(refreshToken)).thenReturn(accountId);
        when(jwtProvider.getAccountId(accessToken)).thenReturn(accountId);

        // logout은 내부적으로 `() -> { ...; return null; }` Supplier를 사용
        // Supplier<T> executeWithLock(String, Duration, Supplier<T>) 오버로드
        when(redisLockService.<Void>executeWithLock(
                eq(LockKeys.reissue(accountId)), any(Duration.class), any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(2);
                    return supplier.get();
                });

        // when
        authService.logout(request, authHeader);

        // then: reissue와 동일한 lock key 사용 확인
        verify(redisLockService).executeWithLock(
                eq(LockKeys.reissue(accountId)), any(Duration.class), any(Supplier.class));
        verify(tokenService).logout(accountId, accessToken);
    }

    @Test
    void logout_refresh_토큰_검증_실패_시_lock_미획득() {
        // given
        String refreshToken = "invalid-refresh";
        String accessToken = "valid-access";
        String authHeader = "Bearer " + accessToken;

        ReissueRequestDto request = mock(ReissueRequestDto.class);
        when(request.getRefreshToken()).thenReturn(refreshToken);

        when(jwtProvider.resolveAccessToken(authHeader)).thenReturn(accessToken);
        when(tokenService.validateRefreshToken(refreshToken))
                .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        // when & then
        assertThatThrownBy(() -> authService.logout(request, authHeader))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(redisLockService, never()).executeWithLock(any(), any(Duration.class), any(Runnable.class));
        verify(tokenService, never()).logout(any(), any());
    }

    @Test
    void logout_access_refresh_토큰_accountId_불일치_시_lock_미획득() {
        // given
        String refreshToken = "refresh-for-account-1";
        String accessToken = "access-for-account-2";
        String authHeader = "Bearer " + accessToken;

        ReissueRequestDto request = mock(ReissueRequestDto.class);
        when(request.getRefreshToken()).thenReturn(refreshToken);

        when(jwtProvider.resolveAccessToken(authHeader)).thenReturn(accessToken);
        when(tokenService.validateRefreshToken(refreshToken)).thenReturn(1L);
        when(jwtProvider.getAccountId(accessToken)).thenReturn(2L); // 불일치

        // when & then
        assertThatThrownBy(() -> authService.logout(request, authHeader))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(redisLockService, never()).executeWithLock(any(), any(Duration.class), any(Runnable.class));
        verify(tokenService, never()).logout(any(), any());
    }

    // ─────────────────── oauthComplete ───────────────────

    @Test
    void oauthComplete_성공_시_oauthTemp_이벤트_발행() throws Exception {
        // given
        String tempToken = "temp-uuid";
        String userInfoJson = "{\"provider\":\"KAKAO\",\"providerId\":\"kakao-id\",\"email\":\"test@test.com\",\"nickname\":\"nick\",\"profileImage\":null}";

        com.eeum.eeum.application.auth.dto.request.OAuthCompleteRequestDto request =
                mock(com.eeum.eeum.application.auth.dto.request.OAuthCompleteRequestDto.class);
        when(request.getTempToken()).thenReturn(tempToken);
        when(request.getName()).thenReturn("홍길동");
        when(request.getPhone()).thenReturn("010-0000-0000");
        when(request.getNickname()).thenReturn(null);

        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("oauth:temp:" + tempToken)).thenReturn(userInfoJson);

        com.eeum.eeum.application.auth.dto.response.OAuthUserInfo userInfo =
                mock(com.eeum.eeum.application.auth.dto.response.OAuthUserInfo.class);
        when(userInfo.getProvider()).thenReturn(com.eeum.eeum.domain.account.enums.OAuthProvider.KAKAO);
        when(userInfo.getProviderId()).thenReturn("kakao-id");
        when(userInfo.getEmail()).thenReturn("test@test.com");
        when(userInfo.getNickname()).thenReturn("nick");
        when(userInfo.getProfileImage()).thenReturn(null);

        when(objectMapper.readValue(userInfoJson, com.eeum.eeum.application.auth.dto.response.OAuthUserInfo.class))
                .thenReturn(userInfo);

        when(accountRepository.findByProviderAndProviderId(
                com.eeum.eeum.domain.account.enums.OAuthProvider.KAKAO, "kakao-id"))
                .thenReturn(Optional.empty());

        when(accountRepository.existsByNickname(any())).thenReturn(false);

        // saveAndFlush()의 반환값은 oauthComplete에서 사용하지 않음 (로컬 account 변수를 그대로 사용)
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        when(jwtProvider.generateAccessToken(any(), any())).thenReturn("access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh");

        // when
        authService.oauthComplete(request);

        // then: oauth:temp 이벤트 발행
        verify(eventPublisher).publishEvent(AccountTokenCleanupEvent.oauthTemp(tempToken));
    }

    @Test
    void oauthComplete_tempToken_만료_시_이벤트_미발행() {
        // given
        String tempToken = "expired-temp";
        com.eeum.eeum.application.auth.dto.request.OAuthCompleteRequestDto request =
                mock(com.eeum.eeum.application.auth.dto.request.OAuthCompleteRequestDto.class);
        when(request.getTempToken()).thenReturn(tempToken);

        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("oauth:temp:" + tempToken)).thenReturn(null); // 만료됨

        // when & then
        assertThatThrownBy(() -> authService.oauthComplete(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── resetPassword 비밀번호 불일치 ───────────────────

    @Test
    void resetPassword_비밀번호와_확인비밀번호_불일치_시_이벤트_미발행() {
        // given
        PasswordNewRequestDto request = mock(PasswordNewRequestDto.class);
        when(request.getNewPassword()).thenReturn("pass1");
        when(request.getNewPasswordConfirm()).thenReturn("pass2"); // 불일치

        // when & then
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_PASSWORD);

        // 확인란 오타로 토큰이 소비되면 사용자가 재설정 메일부터 다시 받아야 한다.
        // 토큰은 검증과 동시에 소비되므로(일회용 보장) 이 검사가 그 앞에 있어야만 성립한다.
        verify(tokenService, never()).consumePasswordResetToken(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
