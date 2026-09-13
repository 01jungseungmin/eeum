package com.eeum.eeum.domain.settlement;

import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.Map;

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

    @Test
    void 취소_전액확인과_누락정산_표식_컬럼이_MySQL에_정확히_생성된다() {
        Map<String, Object> fullCancellation = column("payment_cancellation_operation",
                "full_cancellation_confirmed");
        Map<String, Object> lateReported = column("owner_revenue", "late_settlement_reported_at");

        assertThat(fullCancellation.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(fullCancellation.get("column_default")))
                .isIn("0", "false", "FALSE");
        assertThat(lateReported.get("is_nullable")).isEqualTo("YES");
        assertThat(lateReported.get("data_type")).isEqualTo("datetime");
        assertThat(((Number) lateReported.get("datetime_precision")).intValue()).isEqualTo(6);
        assertThat(appliedMigrationCount()).isEqualTo(2);
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
                WHERE version IN ('13', '14')
                  AND success = 1
                """, Long.class);
    }
}
