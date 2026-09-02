package com.eeum.eeum.domain.chat;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지역 공개 채팅방 목록이 인덱스로 풀리는지 확인한다.
 *
 * <p>이 목록은 지역으로 좁히고 최근 대화순으로 커서 페이징한다. 받쳐주는 인덱스가 없으면
 * MySQL이 {@code chat_room} 전체를 스캔한 뒤 정렬한다(type=ALL, Using filesort) —
 * 방이 늘수록 페이지마다 비용이 커지고, 커서 페이징으로 얻은 이득도 사라진다.
 *
 * <p>결과 정합성 테스트로는 이 문제를 볼 수 없다. 인덱스가 없어도 답은 맞기 때문에
 * 실행 계획 자체를 고정한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatRoomListIndexIntegrationTest extends IntegrationTestSupport {

    // 옵티마이저가 전체 스캔 대신 인덱스를 고를 만한 규모여야 판정에 의미가 있다.
    private static final int ROOMS = 3000;

    private final JdbcTemplate jdbc;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM chat_room");
    }

    @Test
    void 지역_공개_방_목록은_전체_스캔과_정렬_없이_인덱스로_풀린다() {
        // Given
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account creator = accountRepository.save(Account.createUser(
                "idx-" + tag + "@test.com", "pw", "개설자", "개설자" + tag, "010-1234-5678"));
        Region region = regionRepository.save(
                Region.create("1168010" + tag.substring(0, 3), "서울", "강남구", "역삼동", 3));

        List<Object[]> rooms = new ArrayList<>(ROOMS);
        for (int i = 0; i < ROOMS; i++) {
            rooms.add(new Object[]{
                    creator.getAccountId(), "GROUP", "NONE", "방" + i, 1,
                    Timestamp.valueOf(LocalDateTime.now().minusSeconds(i)),
                    Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()),
                    region.getRegionId()});
        }
        jdbc.batchUpdate("""
                INSERT INTO chat_room
                    (created_by, type, ref_type, name, is_active, last_message_at,
                     created_at, modified_at, region_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, rooms);
        jdbc.execute("ANALYZE TABLE chat_room");

        // When: 서비스가 실제로 내보내는 형태 — 지역·활성 필터 + 커서 조건 + 최근 대화순
        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN
                SELECT r.chat_room_id
                FROM chat_room r
                WHERE r.region_id = %d
                  AND r.type IN ('GROUP', 'GROUP_STREET')
                  AND r.is_active = 1
                  AND (r.last_message_at < NOW(6)
                       OR (r.last_message_at = NOW(6) AND r.chat_room_id < 999999)
                       OR r.last_message_at IS NULL)
                ORDER BY r.last_message_at DESC, r.chat_room_id DESC
                LIMIT 21
                """.formatted(region.getRegionId()));

        // Then
        Map<String, Object> row = plan.get(0);
        assertThat(String.valueOf(row.get("key")))
                .as("지역 공개 방 목록을 받쳐주는 인덱스가 사라졌다 — 계획: %s", row)
                .isEqualTo("idx_chat_room_public_list");
        assertThat(String.valueOf(row.get("type")))
                .as("전체 스캔으로 떨어졌다 — 계획: %s", row)
                .isNotEqualTo("ALL");
        assertThat(String.valueOf(row.get("Extra")))
                .as("정렬을 인덱스가 처리하지 못하고 filesort로 떨어졌다 — 계획: %s", row)
                .doesNotContain("Using filesort");
    }
}
