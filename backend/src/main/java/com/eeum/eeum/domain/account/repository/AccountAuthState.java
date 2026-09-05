package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.enums.AccountStatus;

/**
 * 인증 판정에 필요한 최소 정보만 담은 projection.
 *
 * <p>엔티티를 로딩하지 않는다 — WebSocket CONNECT마다, 그리고 세션 대조 주기마다 실행되는 경로다.
 */
public record AccountAuthState(Long accountId, AccountStatus status, Long tokenVersion) {

    public boolean isTokenVersionCurrent(Long tokenVersionClaim) {
        return tokenVersionClaim != null
                && tokenVersionClaim.equals(tokenVersion == null ? 0L : tokenVersion);
    }

    public boolean isUsable(Long tokenVersionClaim) {
        return status == AccountStatus.ACTIVE && isTokenVersionCurrent(tokenVersionClaim);
    }
}
