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
//SpringSecurity가 “현재 로그인한 사용자”를 이해할 수 있도록 변환해주는 클래스
public class CustomUserDetails implements UserDetails {

    private final Long accountId;
    private final String email;
    private final String password;
    private final String role;
    private final AccountStatus status;

    //Account 객체를 입력받아서, 그 안에 있는 값들을 꺼낸 다음 CustomUserDetails 객체 안에 저장
    public CustomUserDetails(Account account) {
        this.accountId = account.getAccountId();
        this.email = account.getEmail();
        this.password = account.getPassword();
        this.role = account.getRole().name();
        this.status = account.getStatus();
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