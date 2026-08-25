package com.eeum.eeum.integration;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * JWT 인증 필터 체인 통합 테스트.
 *
 * <p>{@code GET /accounts/me}는 인증이 필요한 엔드포인트이다(SecurityConfig의 anyRequest().authenticated()).
 * 다양한 계정 상태와 토큰 상태에서 올바른 HTTP 상태 코드가 반환되는지 검증한다.
 *
 * <p>시나리오:
 * <ol>
 *   <li>ACTIVE 계정 + 유효 토큰 → 200</li>
 *   <li>SUSPENDED 계정 + 유효 토큰 → 401 (CustomUserDetails.isEnabled() == false → 인증 실패)</li>
 *   <li>WITHDRAWN 계정 + 유효 토큰 → 401</li>
 *   <li>블랙리스트된 Access Token → 401</li>
 *   <li>토큰 없음 → 401</li>
 * </ol>
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class JwtAuthenticationFilterIntegrationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final AccountRepository accountRepository;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final RedisUtil redisUtil;
    private final PasswordEncoder passwordEncoder;

    private Account activeAccount;
    private Account suspendedAccount;
    private Account withdrawnAccount;

    @BeforeEach
    void setUp() {
        activeAccount = accountRepository.save(
                Account.createUser("active@jwt-test.com", passwordEncoder.encode("pass"), "활성유저", "active_jwt", "010-1111-0001")
        );

        Account suspended = Account.createUser("suspended@jwt-test.com", passwordEncoder.encode("pass"), "정지유저", "suspended_jwt", "010-1111-0002");
        suspended.suspend();
        suspendedAccount = accountRepository.save(suspended);

        Account withdrawn = Account.createUser("withdrawn@jwt-test.com", passwordEncoder.encode("pass"), "탈퇴유저", "withdrawn_jwt", "010-1111-0003");
        withdrawn.withdraw();
        withdrawnAccount = accountRepository.save(withdrawn);
    }

    @AfterEach
    void tearDown() {
        for (Account acc : new Account[]{activeAccount, suspendedAccount, withdrawnAccount}) {
            if (acc != null) {
                Long id = acc.getAccountId();
                redisUtil.delete("refresh:" + id);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 1: ACTIVE 계정 + 유효 Access Token → 200
    // ─────────────────────────────────────────────────────────────────

    @Test
    void ACTIVE_계정_유효_토큰_요청_200() {
        String accessToken = jwtProvider.generateAccessToken(
                activeAccount.getAccountId(),
                activeAccount.getRole().name()
        );

        HttpStatusCode status = getMyPageStatus(accessToken);

        assertThat(status)
                .as("ACTIVE 계정 유효 토큰 → 200 OK")
                .isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 2: SUSPENDED 계정 + 유효 Access Token → 401
    // JwtAuthenticationFilter에서 isEnabled() 확인 후 SecurityContext 미등록
    // ─────────────────────────────────────────────────────────────────

    @Test
    void SUSPENDED_계정_유효_토큰_요청_401() {
        String accessToken = jwtProvider.generateAccessToken(
                suspendedAccount.getAccountId(),
                suspendedAccount.getRole().name()
        );

        HttpStatusCode status = getMyPageStatus(accessToken);

        assertThat(status)
                .as("SUSPENDED 계정 유효 토큰 → 401 Unauthorized")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 3: WITHDRAWN 계정 + 유효 Access Token → 401
    // ─────────────────────────────────────────────────────────────────

    @Test
    void WITHDRAWN_계정_유효_토큰_요청_401() {
        String accessToken = jwtProvider.generateAccessToken(
                withdrawnAccount.getAccountId(),
                withdrawnAccount.getRole().name()
        );

        HttpStatusCode status = getMyPageStatus(accessToken);

        assertThat(status)
                .as("WITHDRAWN 계정 유효 토큰 → 401 Unauthorized")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 4: 블랙리스트된 Access Token → 401
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 블랙리스트_토큰_요청_401() {
        String accessToken = jwtProvider.generateAccessToken(
                activeAccount.getAccountId(),
                activeAccount.getRole().name()
        );

        // 블랙리스트 등록
        tokenService.blacklistAccessToken(accessToken);

        HttpStatusCode status = getMyPageStatus(accessToken);

        assertThat(status)
                .as("블랙리스트 토큰 → 401 Unauthorized")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 5: 토큰 없음 → 401
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 토큰_없음_요청_401() {
        HttpStatusCode status = getMyPageStatusWithoutToken();

        assertThat(status)
                .as("토큰 없음 → 401 Unauthorized")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────

    /**
     * Bearer 토큰을 포함한 GET /accounts/me 요청을 보내고 HTTP 상태 코드를 반환한다.
     *
     * <p>MockMvc를 쓴다 — 검증 대상은 Security 필터 체인(JwtAuthenticationFilter)이고
     * MockMvc도 그 체인을 그대로 통과시킨다. RANDOM_PORT로 실제 서블릿 컨테이너를 띄우면
     * 이 클래스만 별도 Spring 컨텍스트를 쓰게 되어 컨텍스트 로딩이 한 번 더 일어난다.
     */
    private HttpStatusCode getMyPageStatus(String accessToken) {
        return request(get("/accounts/me").header("Authorization", "Bearer " + accessToken));
    }

    private HttpStatusCode request(MockHttpServletRequestBuilder builder) {
        try {
            return HttpStatusCode.valueOf(
                    mockMvc.perform(builder).andReturn().getResponse().getStatus());
        } catch (Exception e) {
            throw new IllegalStateException("요청 실행 실패", e);
        }
    }

    private HttpStatusCode getMyPageStatusWithoutToken() {
        return request(get("/accounts/me"));
    }
}
