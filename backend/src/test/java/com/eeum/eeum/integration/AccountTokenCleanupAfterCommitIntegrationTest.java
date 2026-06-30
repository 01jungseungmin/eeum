package com.eeum.eeum.integration;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.service.AccountService;
import com.eeum.eeum.application.auth.dto.request.PasswordNewRequestDto;
import com.eeum.eeum.application.auth.service.AuthService;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * AFTER_COMMIT 이벤트 기반 Redis 토큰 정리 통합 테스트.
 *
 * <p>AccountTokenCleanupEventListener는 @Async + @TransactionalEventListener(AFTER_COMMIT)
 * 로 동작하므로, DB 커밋 직후 비동기로 Redis 키를 삭제한다.
 * 따라서 검증 시 Awaitility로 최대 5초 대기한다.
 *
 * <p><b>주의:</b> 이 클래스에는 {@code @Transactional}을 붙이지 않는다.
 * @Transactional이 있으면 트랜잭션이 커밋되지 않아 AFTER_COMMIT 리스너가 실행되지 않는다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AccountTokenCleanupAfterCommitIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private final AuthService authService;
    private final AccountService accountService;
    private final TokenService tokenService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;

    private static final String RAW_PASSWORD = "Test1234!";

    private Account savedAccount;

    @BeforeEach
    void setUp() {
        savedAccount = accountRepository.save(
                Account.createUser(
                        "cleanup-test@eeum.com",
                        passwordEncoder.encode(RAW_PASSWORD),
                        "정리테스터",
                        "cleanup_tester",
                        "010-9999-0001"
                )
        );
    }

    @AfterEach
    void tearDown() {
        // Redis 키 정리 (남아있을 수 있는 키 모두 삭제)
        Long id = savedAccount.getAccountId();
        redisUtil.delete("refresh:" + id);
        redisUtil.delete("reauth:" + id);
        redisUtil.delete("password-reset:" + id);

        accountRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 1: resetPassword 커밋 성공 시 Redis 토큰 삭제
    // ─────────────────────────────────────────────────────────────────

    @Test
    void resetPassword_커밋_성공_시_refresh_및_passwordReset_토큰_삭제() {
        // given
        Long accountId = savedAccount.getAccountId();

        // Refresh Token, Password Reset Token 을 Redis에 직접 저장
        String refreshToken = jwtProvider.generateRefreshToken(accountId);
        tokenService.saveRefreshToken(accountId, refreshToken);

        String passwordResetToken = tokenService.generateAndSavePasswordResetToken(accountId);

        assertThat(redisUtil.hasKey("refresh:" + accountId)).isTrue();
        assertThat(redisUtil.hasKey("password-reset:" + accountId)).isTrue();

        PasswordNewRequestDto request = buildPasswordNewRequest(passwordResetToken, "NewPass5678!", "NewPass5678!");

        // when
        authService.resetPassword(request);

        // then: AFTER_COMMIT 비동기 실행 완료까지 최대 5초 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redisUtil.hasKey("refresh:" + accountId))
                    .as("resetPassword 후 refresh 토큰 삭제 확인").isFalse();
            assertThat(redisUtil.hasKey("password-reset:" + accountId))
                    .as("resetPassword 후 password-reset 토큰 삭제 확인").isFalse();
        });

        // DB 비밀번호 변경 확인
        Account updated = accountRepository.findById(accountId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass5678!", updated.getPassword())).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 2: resetPassword 잘못된 토큰 → 예외 시 Redis 토큰 유지
    // ─────────────────────────────────────────────────────────────────

    @Test
    void resetPassword_유효하지_않은_토큰_시_예외_발생하고_Redis_토큰_유지() {
        // given
        Long accountId = savedAccount.getAccountId();

        String refreshToken = jwtProvider.generateRefreshToken(accountId);
        tokenService.saveRefreshToken(accountId, refreshToken);

        // password-reset 토큰도 저장 (이 토큰과 다른 값을 전달해서 예외 유발)
        tokenService.generateAndSavePasswordResetToken(accountId);

        // 만료된/잘못된 토큰으로 생성 — 유효하지 않은 JWT 문자열 사용
        String invalidResetToken = "invalid.token.value";
        PasswordNewRequestDto request = buildPasswordNewRequest(invalidResetToken, "NewPass5678!", "NewPass5678!");

        // when & then: 예외 발생
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class);

        // 이벤트 미발행이므로 AFTER_COMMIT 미실행 → Redis 키 즉시 유지 확인
        assertThat(redisUtil.hasKey("refresh:" + accountId))
                .as("예외 발생 시 refresh 토큰 유지").isTrue();
        assertThat(redisUtil.hasKey("password-reset:" + accountId))
                .as("예외 발생 시 password-reset 토큰 유지").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 3: changePassword 커밋 성공 시 Redis 토큰 삭제
    // ─────────────────────────────────────────────────────────────────

    @Test
    void changePassword_커밋_성공_시_refresh_및_reAuth_토큰_삭제() {
        // given
        Long accountId = savedAccount.getAccountId();

        // Refresh Token, ReAuth Token Redis에 저장
        String refreshToken = jwtProvider.generateRefreshToken(accountId);
        tokenService.saveRefreshToken(accountId, refreshToken);

        String reAuthToken = tokenService.generateAndSaveReAuthToken(accountId);

        assertThat(redisUtil.hasKey("refresh:" + accountId)).isTrue();
        assertThat(redisUtil.hasKey("reauth:" + accountId)).isTrue();

        ChangePasswordRequestDto request = buildChangePasswordRequest(reAuthToken, RAW_PASSWORD, "NewPass5678!");

        // when
        accountService.changePassword(accountId, request);

        // then: AFTER_COMMIT 비동기 완료까지 최대 5초 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redisUtil.hasKey("refresh:" + accountId))
                    .as("changePassword 후 refresh 토큰 삭제 확인").isFalse();
            assertThat(redisUtil.hasKey("reauth:" + accountId))
                    .as("changePassword 후 reauth 토큰 삭제 확인").isFalse();
        });

        // DB 비밀번호 변경 확인
        Account updated = accountRepository.findById(accountId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass5678!", updated.getPassword())).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 4: withdraw 커밋 성공 시 Redis 토큰 삭제 및 계정 상태 변경
    // ─────────────────────────────────────────────────────────────────

    @Test
    void withdraw_커밋_성공_시_refresh_및_reAuth_토큰_삭제_및_계정상태_WITHDRAWN() {
        // given
        Long accountId = savedAccount.getAccountId();

        String refreshToken = jwtProvider.generateRefreshToken(accountId);
        tokenService.saveRefreshToken(accountId, refreshToken);

        String reAuthToken = tokenService.generateAndSaveReAuthToken(accountId);

        assertThat(redisUtil.hasKey("refresh:" + accountId)).isTrue();
        assertThat(redisUtil.hasKey("reauth:" + accountId)).isTrue();

        WithdrawRequestDto request = buildWithdrawRequest(reAuthToken);

        // when
        accountService.withdraw(accountId, request);

        // then: DB 상태 즉시 확인
        Account withdrawn = accountRepository.findById(accountId).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(withdrawn.getDeletedAt()).isNotNull();

        // then: AFTER_COMMIT 비동기 완료까지 최대 5초 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redisUtil.hasKey("refresh:" + accountId))
                    .as("withdraw 후 refresh 토큰 삭제 확인").isFalse();
            assertThat(redisUtil.hasKey("reauth:" + accountId))
                    .as("withdraw 후 reauth 토큰 삭제 확인").isFalse();
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────

    private PasswordNewRequestDto buildPasswordNewRequest(
            String passwordResetToken,
            String newPassword,
            String newPasswordConfirm
    ) {
        PasswordNewRequestDto dto = new PasswordNewRequestDto();
        ReflectionTestUtils.setField(dto, "passwordResetToken", passwordResetToken);
        ReflectionTestUtils.setField(dto, "newPassword", newPassword);
        ReflectionTestUtils.setField(dto, "newPasswordConfirm", newPasswordConfirm);
        return dto;
    }

    private ChangePasswordRequestDto buildChangePasswordRequest(
            String reAuthToken,
            String currentPassword,
            String newPassword
    ) {
        ChangePasswordRequestDto dto = new ChangePasswordRequestDto();
        ReflectionTestUtils.setField(dto, "reAuthToken", reAuthToken);
        ReflectionTestUtils.setField(dto, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(dto, "newPassword", newPassword);
        return dto;
    }

    private WithdrawRequestDto buildWithdrawRequest(String reAuthToken) {
        WithdrawRequestDto dto = new WithdrawRequestDto();
        ReflectionTestUtils.setField(dto, "reAuthToken", reAuthToken);
        return dto;
    }
}
