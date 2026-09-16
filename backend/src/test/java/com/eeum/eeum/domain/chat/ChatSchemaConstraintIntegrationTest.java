package com.eeum.eeum.domain.chat;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 동시성 방어의 최종 방어선인 DB 제약이 <b>실제로 생성되는지</b> 검증한다.
 * <p>
 * 엔티티에 {@code @UniqueConstraint}가 선언돼 있다는 것과 DB에 인덱스가 존재한다는 것은 별개다.
 * 특히 {@code ddl-auto=update}는 유니크 인덱스 생성 실패를 로그만 남기고 넘어가므로,
 * 선언만 보고 방어가 걸려 있다고 단정할 수 없다.
 * <p>
 * 이 테스트는 엔티티 정의만으로 스키마를 만드는 환경(create-drop)에서
 * Hibernate가 실제로 무엇을 만드는지를 information_schema로 확인한다.
 * 운영 DB는 init.sql + ddl-auto=update로 만들어지므로 별도 점검이 필요하다
 * (배포 전/후 확인 SQL은 각 엔티티 주석 참고).
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatSchemaConstraintIntegrationTest extends IntegrationTestSupport {


    private final JdbcTemplate jdbcTemplate;

    // ===================== chat_room =====================

    @Test
    void active_ref_key는_STORED_생성컬럼으로_만들어진다() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT EXTRA, GENERATION_EXPRESSION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_room'
                  AND COLUMN_NAME = 'active_ref_key'
                """);

        assertThat(String.valueOf(column.get("EXTRA")))
                .as("가상 컬럼이면 인덱싱 성능이 달라진다 — STORED여야 한다")
                .contains("STORED");

        String expression = String.valueOf(column.get("GENERATION_EXPRESSION"));
        assertThat(expression).contains("is_active").contains("STORE").contains("ref_id");
        // type이 들어가면 한 가게가 GROUP/GROUP_STREET 방을 동시에 ACTIVE로 가질 수 있고,
        // 상점 상세/대시보드는 type 무관 최신 1건을 노출하므로 노출 방과 생성 방이 갈린다.
        assertThat(expression)
                .as("생성식에 type이 포함되면 가게당 ACTIVE 방이 2개가 될 수 있다")
                .doesNotContain("`type`");
    }

    @Test
    void active_ref_key에_UNIQUE_인덱스가_실제로_존재한다() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
                SELECT INDEX_NAME, NON_UNIQUE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_room'
                  AND COLUMN_NAME = 'active_ref_key'
                """);

        assertThat(indexes)
                .as("uk_chat_room_active_ref가 없으면 Redis 락 유실 시 ACTIVE 단톡방 중복을 막을 수 없다")
                .isNotEmpty();
        assertThat(indexes).allSatisfy(row ->
                assertThat(((Number) row.get("NON_UNIQUE")).intValue())
                        .as("UNIQUE가 아니면 중복 차단 효과가 없다")
                        .isZero());
    }

    // ===================== chat_participant =====================

    @Test
    void chat_participant에_방_계정_UNIQUE_인덱스가_정확히_하나_존재한다() {
        List<Map<String, Object>> uniqueIndexes = jdbcTemplate.queryForList("""
                SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLS
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_participant'
                  AND NON_UNIQUE = 0
                  AND INDEX_NAME <> 'PRIMARY'
                GROUP BY INDEX_NAME
                HAVING COLS = 'chat_room_id,account_id'
                """);

        // 2개 이상이면 동일 컬럼 유니크 인덱스가 중복 생성된 것 —
        // INSERT마다 불필요한 인덱스 유지 비용이 든다 (엔티티 제약명이 init.sql과 어긋날 때 발생).
        assertThat(uniqueIndexes)
                .as("중복 참여자 방어 인덱스는 정확히 1개여야 한다 (0=방어 없음, 2+=중복 인덱스)")
                .hasSize(1);
    }
}
