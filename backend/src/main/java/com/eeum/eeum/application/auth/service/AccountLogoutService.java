package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountLogoutService {
    private final AccountRepository accountRepository;
    private final TokenService tokenService;
    private final JwtProvider jwtProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void logout(Long accountId, String accessToken, String refreshToken) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.assertWritable();
        if (!account.isTokenVersionCurrent(jwtProvider.getTokenVersion(accessToken))
                || !account.isTokenVersionCurrent(jwtProvider.getTokenVersion(refreshToken))
                || !accountId.equals(tokenService.validateRefreshToken(refreshToken))) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        // 이 기기로 가던 알림도 함께 끊는다. 남겨 두면 다른 계정으로 전환한 기기에
        // 이 계정의 알림이 계속 도착한다.
        account.updateFcmToken(null);

        // BEFORE_COMMIT 회수로 Redis 데이터가 사라져도 이 계정의 기존 세션은 되살아나지 않는다.
        eventPublisher.publishEvent(AccountTokenCleanupEvent.allTokens(accountId));
    }
}
