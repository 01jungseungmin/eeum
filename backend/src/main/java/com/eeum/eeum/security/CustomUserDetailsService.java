package com.eeum.eeum.security;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
//JWT 토큰 안에 들어있는 accountId로 DB에서 회원을 찾아서 Spring Security가 이해할 수 있는 사용자 객체로 바꿔주는 클래스
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    // username = accountId 문자열 (JWT subject)
    @Override
    @Transactional(readOnly = true) //DB에서 회원을 조회만 하니까 읽기 전용 트랜잭션, JPA 변경 감지 부담 감소
    public UserDetails loadUserByUsername(String accountId) throws UsernameNotFoundException {//Spring Security가 사용자 정보를 가져올 때 호출하는 메서드
        Long parsedAccountId = parseAccountId(accountId); //AccountRepository.findById()는 보통 Long 타입 ID를 필요 -> 문자열을 Long으로 변환

        Account account = accountRepository.findById(parsedAccountId)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: " + accountId)); //DB에서 accountId로 회원을 찾아서 있으면 반환 없으면 UsernameNotFoundException

        return new CustomUserDetails(account); //Spring Security는 UserDetails 타입을 이해
    }

    private Long parseAccountId(String accountId) {
        try {
            return Long.parseLong(accountId);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("잘못된 회원 ID 형식입니다: " + accountId, e);
        }
    }
}