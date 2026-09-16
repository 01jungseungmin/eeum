package com.eeum.eeum.security;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long accountId;
    private final String email;
    private final String password;
    private final String role;
    private final AccountStatus status;
    private final Long tokenVersion;

    public CustomUserDetails(Account account) {
        this.accountId = account.getAccountId();
        this.email = account.getEmail();
        this.password = account.getPassword();
        this.role = account.getRole().name();
        this.status = account.getStatus();
        this.tokenVersion = account.getTokenVersion();
    }

    /**
     * 이 토큰이 현재 세대인지. 회수된 세대면 인증하지 않는다.
     *
     * 계정 상태만 보면 정지·탈퇴는 걸러지지만 비밀번호 재설정·권한 변경은 걸러지지 않는다.
     * Redis에서 Refresh Token을 지우는 것으로 처리해 왔는데 그 삭제는 비동기라 보장되지 않는다.
     */
    public boolean isTokenVersionCurrent(Long tokenVersionClaim) {
        return tokenVersionClaim != null
                && tokenVersionClaim.equals(tokenVersion == null ? 0L : tokenVersion);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getUsername() {
        return String.valueOf(accountId);
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return status != AccountStatus.SUSPENDED
                && status != AccountStatus.WITHDRAWN;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
