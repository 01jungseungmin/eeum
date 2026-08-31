package com.eeum.eeum.security;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

@Getter
//SpringSecurity가 “현재 로그인한 사용자”를 이해할 수 있도록 변환해주는 클래스
public class CustomUserDetails implements UserDetails {

    private final Long accountId;
    private final String email;
    private final String password;
    private final String role;
    private final AccountStatus status;
    // 계정 엔티티를 들고 있지 않으므로 판정에 필요한 값만 복사한다.
    private final LocalDateTime tokenInvalidatedAt;

    //Account 객체를 입력받아서, 그 안에 있는 값들을 꺼낸 다음 CustomUserDetails 객체 안에 저장
    public CustomUserDetails(Account account) {
        this.accountId = account.getAccountId();
        this.email = account.getEmail();
        this.password = account.getPassword();
        this.role = account.getRole().name();
        this.status = account.getStatus();
        this.tokenInvalidatedAt = account.getTokenInvalidatedAt();
    }

    /**
     * 이 토큰이 회수 대상인지. 무효화 시각 이전에 발급된 토큰은 전부 무효다.
     *
     * <p>계정 상태만 보면 정지·탈퇴는 걸러지지만 비밀번호 재설정·권한 변경은 걸러지지 않는다.
     * Redis에서 Refresh Token을 지우는 것으로 처리해 왔는데, 그 삭제는 비동기 풀을 타므로
     * 보장되지 않는다. 이 판정이 최종 근거다.
     */
    public boolean isTokenInvalidated(Instant issuedAt) {
        if (tokenInvalidatedAt == null || issuedAt == null) {
            return false;
        }
        Instant invalidatedAt = tokenInvalidatedAt.atZone(ZoneId.systemDefault()).toInstant();
        // iat는 초 단위라 같은 초면 구분할 수 없다 — 살려두기보다 막는 쪽을 택한다.
        return !issuedAt.truncatedTo(ChronoUnit.SECONDS)
                .isAfter(invalidatedAt.truncatedTo(ChronoUnit.SECONDS));
    }

    @Override
    // 현재 사용자의 권한 목록을 반환하는 메서드
    // role = "ROLE_ADMIN" -> [ROLE_ADMIN]
    // hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 검사
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    // Spring Security에서 사용하는 사용자 식별값을 반환하는 메서드
    // 인증 이후에는 이메일보다 accountId가 더 안정적인 식별자이므로 accountId를 문자열로 반환
    public String getUsername() {
        return String.valueOf(accountId);
    }

    @Override
    // 계정이 만료되지 않았는지 나타내는 메서드
    // 현재 프로젝트에서는 계정 만료 정책이 없으므로 항상 true
    public boolean isAccountNonExpired() { return true; }

    @Override
    // SUSPENDED → 잠긴 계정 (LockedException)
    // WITHDRAWN → 잠긴 계정 (LockedException)
    public boolean isAccountNonLocked() {
        return status != AccountStatus.SUSPENDED
                && status != AccountStatus.WITHDRAWN;
    }

    @Override
    // 계정이 활성화되어 있는지 나타내는 메서드
    // 새로운 상태가 추가되더라도 명시적으로 ACTIVE만 허용 (화이트리스트)
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }

    @Override
    // 비밀번호 같은 인증 정보가 만료되지 않았는지 나타내는 메서드
    // 현재 프로젝트에서는 비밀번호 만료 정책이 없으므로 항상 true
    public boolean isCredentialsNonExpired() {
        return true;
    }
}