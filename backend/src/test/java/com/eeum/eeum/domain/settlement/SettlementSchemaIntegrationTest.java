package com.eeum.eeum.domain.settlement;

import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway V13/V14가 실제 MySQL 스키마를 만든다.
 *
 * 테스트 프로필의 Hibernate DDL 생성이 컬럼을 대신 만들면 migration 누락도 통과한다.
 * 이 클래스는 validate로 고정해 Flyway 이력과 information_schema 제약을 함께 검증한다.
 */
@EnabledIfDockerAvailable
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@RequiredArgsConstructor
class SettlementSchemaIntegrationTest extends IntegrationTestSupport {

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;

    private Store fixtureStore;

    @AfterEach
    void tearDown() {
        if (fixtureStore != null && fixtureStore.getStoreId() != null) {
            jdbcTemplate.update("DELETE FROM weekly_settlement WHERE store_id = ?", fixtureStore.getStoreId());
            storeRepository.deleteById(fixtureStore.getStoreId());
            accountRepository.deleteById(fixtureStore.getAccount().getAccountId());
        }
    }

    @Test
    void 취소_전액확인과_누락정산_표식_컬럼이_MySQL에_정확히_생성된다() {
        Map<String, Object> fullCancellation = column("payment_cancellation_operation",
                "full_cancellation_confirmed");
        Map<String, Object> partialCancellation = column("payment_cancellation_operation", "pg_cancelled_amount");
        Map<String, Object> paymentCancellation = column("payment", "cancelled_amount");
        Map<String, Object> pgFeeRate = column("owner_revenue", "pg_fee_rate");
        Map<String, Object> platformFeeRate = column("owner_revenue", "platform_fee_rate");
        Map<String, Object> lateReported = column("owner_revenue", "late_settlement_reported_at");

        assertThat(fullCancellation.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(fullCancellation.get("column_default")))
                .isIn("0", "false", "FALSE");
        assertThat(partialCancellation.get("is_nullable")).isEqualTo("YES");
        assertThat(paymentCancellation.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(paymentCancellation.get("column_default")))
                .isIn("0", "0.00", "0.0000");
        assertThat(pgFeeRate.get("is_nullable")).isEqualTo("NO");
        assertThat(platformFeeRate.get("is_nullable")).isEqualTo("NO");
        assertThat(lateReported.get("is_nullable")).isEqualTo("YES");
        assertThat(lateReported.get("data_type")).isEqualTo("datetime");
        assertThat(((Number) lateReported.get("datetime_precision")).intValue()).isEqualTo(6);
        assertThat(appliedMigrationCount()).isEqualTo(4);
    }

    @Test
    @Transactional
    void 동일_상점과_주차의_네이티브_upsert는_정산_행을_하나만_만든다() {
        Account owner = accountRepository.save(Account.createOwner(
                "schema-" + UUID.randomUUID() + "@test.com", "password", "스키마 점주", "010-0000-0000"));
        fixtureStore = storeRepository.save(Store.createForOwnerSignup(
                owner, "스키마 상점", "서울시", "02-0000-0000"));
        LocalDateTime start = LocalDateTime.of(2026, 9, 7, 0, 0);
        LocalDateTime end = start.plusWeeks(1);
        String key = "schema:" + fixtureStore.getStoreId() + ":" + end;

        weeklySettlementRepository.insertIfAbsent(fixtureStore.getStoreId(), start, end, key);
        weeklySettlementRepository.insertIfAbsent(fixtureStore.getStoreId(), start, end, key);

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM weekly_settlement
                WHERE store_id = ? AND period_start_at = ? AND period_end_at = ?
                """, Integer.class, fixtureStore.getStoreId(), start, end);
        assertThat(count).isEqualTo(1);
    }

    private Map<String, Object> column(String tableName, String columnName) {
        return jdbcTemplate.queryForMap("""
                SELECT is_nullable, column_default, data_type, datetime_precision
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, tableName, columnName);
    }

    private Long appliedMigrationCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('13', '14', '15', '16')
                  AND success = 1
                """, Long.class);
    }
}
