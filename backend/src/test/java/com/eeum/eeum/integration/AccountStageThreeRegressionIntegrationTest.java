package com.eeum.eeum.integration;

import com.eeum.eeum.application.auth.dto.request.ReissueRequestDto;
import com.eeum.eeum.application.auth.service.AuthService;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.service.OrderService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.jwt.JwtProvider;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AccountStageThreeRegressionIntegrationTest extends IntegrationTestSupport {
    private final AccountRepository accounts;
    private final AuthService auth;
    private final TokenService tokens;
    private final JwtProvider jwt;
    private final RedisUtil redis;
    private final MockMvc mvc;
    private final OrderService orders;
    private final PlatformTransactionManager transactionManager;

    private Account account() {
        String key = UUID.randomUUID().toString();
        return accounts.save(Account.createUser(key + "@test.com", "encoded", "사용자", key, "010"));
    }

    @Test
    void 로그아웃후_Redis_회수정보가_없어도_이전_access는_거절된다() throws Exception {
        Account account = account();
        long id = account.getAccountId();
        String access = jwt.generateAccessToken(id, "ROLE_USER", 0L);
        String refresh = jwt.generateRefreshToken(id, 0L);
        tokens.saveRefreshToken(id, refresh);
        ReissueRequestDto request = new ReissueRequestDto();
        ReflectionTestUtils.setField(request, "refreshToken", refresh);
        auth.logout(request, "Bearer " + access);
        redis.delete("refresh:" + id);
        redis.delete("blacklist:access:" + access);
        redis.delete("blacklist:access:fingerprint:" + tokens.accessTokenFingerprint(access));
        assertThat(accounts.findById(id).orElseThrow().getTokenVersion()).isEqualTo(1L);
        mvc.perform(get("/accounts/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴_커밋을_기다린_주문은_재고처리_전에_거절된다() throws Exception {
        Account account = account();
        OrderCreateRequestDto request = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(request, "paymentMethod", PaymentMethod.CARD);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> withdraw = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                Account current = accounts.findByIdWithLock(account.getAccountId()).orElseThrow();
                current.withdraw();
                locked.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> order = pool.submit(() -> orders.createOrder(account.getAccountId(), request));
            assertThat(awaitLockWait("account")).isTrue();
            release.countDown();
            withdraw.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> order.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
