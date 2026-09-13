package com.eeum.eeum.domain.settlement;

import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway V13/V14가 실제 MySQL 스키마에 반영되는지 검증한다.
 *
 * <p>단위 테스트는 JPA 매핑만 검증하므로, 운영 DB에서 NOT NULL/DEFAULT 또는 컬럼 누락이
 * 발생하는 문제를 잡지 못한다. 이 테스트는 Flyway가 적용된 MySQL의 information_schema를
 * 직접 조회해 취소 전액 확인값과 누락 정산 표식의 제약을 고정한다.
 */
@EnabledIfDockerAvailable
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
    }

    private Map<String, Object> column(String tableName, String columnName) {
        return jdbcTemplate.queryForMap("""
                SELECT is_nullable, column_default, data_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, tableName, columnName);
    }
}
