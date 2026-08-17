package com.eeum.eeum.domain.operation.repository;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 실패 이력 조회 — 실제 MySQL Testcontainer 환경에서 실행.
 *
 * <p>Mock 단위 테스트로는 신규 테이블 DDL 생성, QueryDSL 조건 조합,
 * groupBy 집계, 보존기간 bulk delete를 검증할 수 없어 통합 테스트로 확인한다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class OperationFailureLogRepositoryIntegrationTest {

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

    private final OperationFailureLogRepository repository;

    // createdAt은 updatable = false다. JPA 경로로는 과거 시각을 심을 수 없어 SQL로 직접 갱신한다.
    private final JdbcTemplate jdbcTemplate;

    // deleteOlderThan은 @Modifying 벌크 쿼리라 활성 트랜잭션이 필요하다.
    // 운영에서는 OperationFailureLogCleanupScheduler의 @Transactional이 그 역할을 하므로
    // 테스트도 같은 방식으로 트랜잭션을 열어 호출한다.
    private final TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void 카테고리_필터가_해당_분류만_반환한다() {
        save(OperationFailureCategory.REFUND, "PaymentService.cancelPayment", "PORTONE_TIMEOUT");
        save(OperationFailureCategory.SCHEDULER, "OrderExpirationScheduler.expire", "NPE");
        save(OperationFailureCategory.SCHEDULER, "AccountCleanupScheduler.clean", "NPE");

        Page<OperationFailureLog> result = repository.searchFailures(
                OperationFailureCategory.SCHEDULER, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(log -> log.getCategory() == OperationFailureCategory.SCHEDULER);
    }

    @Test
    void 키워드는_작업명_에러코드_메시지를_모두_검색한다() {
        save(OperationFailureCategory.REFUND, "PaymentService.cancelPayment", "PORTONE_TIMEOUT");
        save(OperationFailureCategory.EXTERNAL_API, "NtsClient.verify", "CONNECT_FAILED");

        assertThat(repository.searchFailures(null, null, null, "cancelPayment", PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
        assertThat(repository.searchFailures(null, null, null, "PORTONE_TIMEOUT", PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
        assertThat(repository.searchFailures(null, null, null, "없는키워드", PageRequest.of(0, 20))
                .getTotalElements()).isZero();
    }

    @Test
    void 기간_필터는_구간_밖_이력을_제외한다() {
        OperationFailureLog old = save(
                OperationFailureCategory.REFUND, "old.operation", "OLD");
        backdate(old, LocalDateTime.now().minusDays(10));
        save(OperationFailureCategory.REFUND, "recent.operation", "RECENT");

        Page<OperationFailureLog> result = repository.searchFailures(
                null, LocalDateTime.now().minusDays(1), null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getOperation()).isEqualTo("recent.operation");
    }

    @Test
    void 카테고리별_집계는_발생하지_않은_분류도_0으로_채운다() {
        save(OperationFailureCategory.REFUND, "a", "E1");
        save(OperationFailureCategory.REFUND, "b", "E2");
        save(OperationFailureCategory.SCHEDULER, "c", "E3");

        Map<OperationFailureCategory, Long> counts =
                repository.countByCategorySince(LocalDateTime.now().minusHours(1));

        assertThat(counts).containsOnlyKeys(OperationFailureCategory.values());
        assertThat(counts.get(OperationFailureCategory.REFUND)).isEqualTo(2L);
        assertThat(counts.get(OperationFailureCategory.SCHEDULER)).isEqualTo(1L);
        assertThat(counts.get(OperationFailureCategory.PAYMENT_WEBHOOK)).isZero();
        assertThat(counts.get(OperationFailureCategory.EXTERNAL_API)).isZero();
    }

    @Test
    void 최근_실패는_최신순으로_limit만큼_반환한다() {
        for (int i = 0; i < 5; i++) {
            save(OperationFailureCategory.SCHEDULER, "op-" + i, "E" + i);
        }

        List<OperationFailureLog> recent = repository.findRecentFailures(3);

        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).getCreatedAt())
                .isAfterOrEqualTo(recent.get(recent.size() - 1).getCreatedAt());
    }

    @Test
    void 보존기간이_지난_이력만_물리_삭제된다() {
        OperationFailureLog old = save(OperationFailureCategory.REFUND, "old", "OLD");
        backdate(old, LocalDateTime.now().minusMonths(4));
        save(OperationFailureCategory.REFUND, "recent", "RECENT");

        LocalDateTime threshold = LocalDateTime.now().minusMonths(3);
        int deleted = transactionTemplate.execute(status -> repository.deleteOlderThan(threshold));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getOperation()).isEqualTo("recent");
    }

    // ─────────────────── 헬퍼 ───────────────────

    private OperationFailureLog save(
            OperationFailureCategory category, String operation, String errorCode) {
        return repository.saveAndFlush(OperationFailureLog.create(
                category, operation, "PAYMENT", "1", errorCode, "메시지", "payload"));
    }

    /**
     * createdAt을 과거로 옮긴다.
     *
     * <p>BaseEntity의 createdAt은 {@code updatable = false}라 엔티티 필드를 고쳐 저장해도
     * Hibernate가 UPDATE 문에서 그 컬럼을 빼버린다 — 그래서 SQL로 직접 갱신한다.
     * 조회 검증이 뒤따르므로 영속성 컨텍스트에 남은 낡은 값도 함께 비운다.
     */
    private void backdate(OperationFailureLog log, LocalDateTime createdAt) {
        int updated = jdbcTemplate.update(
                "update operation_failure_log set created_at = ? where operation_failure_log_id = ?",
                Timestamp.valueOf(createdAt), log.getOperationFailureLogId());
        assertThat(updated).isEqualTo(1);
    }
}
