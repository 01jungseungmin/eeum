-- ============================================================================
-- 지역 공개 채팅방 목록 전용 인덱스 추가
--
-- 배경
--   GET /chat/rooms/public은 region_id와 is_active를 등호로 걸고
--   last_message_at DESC, chat_room_id DESC로 커서 페이징한다.
--   chat_room에는 idx_chat_room_ref(ref_type, ref_id, is_active)뿐이라 지역으로 좁힐
--   인덱스가 없었다. 정렬 키도 인덱스 밖이라 매 페이지가 전체 스캔 + 정렬이었다.
--
-- 실측 (MySQL 8.0, chat_room 3000행, LIMIT 21)
--   추가 전: type=ALL   key=NULL                      rows=3000  Extra=Using filesort
--            → 지역과 무관한 방까지 전부 읽고 정렬한다. 방이 늘수록 페이지마다 비용이 커진다.
--   추가 후: type=range key=idx_chat_room_public_list rows=2
--            Extra=Using index condition; Backward index scan
--            → 커서 조건이 range로 풀리고 정렬은 인덱스가 처리한다. filesort가 사라진다.
--
--   커서 페이징으로 바꾼 이유가 "페이지마다 앞부분을 다시 읽지 않는 것"인데,
--   인덱스가 없으면 그 이득이 사라진다. 정확성 문제가 아니라 비용 곡선 문제다.
--
-- 컬럼 순서
--   등호 조건(region_id, is_active)을 앞에, 정렬 키(last_message_at, chat_room_id)를 뒤에 둔다.
--   type IN ('GROUP','GROUP_STREET')은 넣지 않는다 — 범위성 조건을 정렬 키 앞에 두면
--   인덱스 정렬이 깨져 filesort가 되돌아온다. type은 읽은 행에서 걸러도 충분하다
--   (PRIVATE 문의방만 제외하므로 선택도가 낮다).
--
-- 왜 "내 채팅방 목록"에는 이 인덱스가 도움이 되지 않는가
--   GET /chat/rooms는 chat_participant에서 출발해 chat_room을 조인한다. 정렬 키가 조인
--   반대편에 있어 인덱스로 정렬을 풀 수 없다(추가 후에도 Using filesort로 남는다).
--   다만 정렬 대상이 "내가 속한 방 수"로 한정되므로 실질 비용은 작다.
--   이 인덱스는 공개 방 탐색 전용이다.
--
-- 비용
--   chat_room은 방 생성·종료·메시지 수신(last_message_at 갱신) 시 쓰기가 일어난다.
--   last_message_at이 인덱스에 포함되므로 메시지가 올 때마다 인덱스 유지 비용이 붙는다.
--   그 대신 공개 방 탐색이 전체 스캔에서 벗어난다. 방 수가 수백 건 미만이면 급하지 않다.
--
-- 적용 시점
--   운영 반영은 서비스 영향이 적은 시간대에 한다. ALTER는 온라인 DDL(INPLACE)로 동작하지만
--   테이블 크기에 따라 시간이 걸린다. ddl-auto=update가 기존 테이블에 인덱스를 붙여줄지는
--   보장되지 않으므로(2026-08-25 런북의 경고 참고) 배포 전에 이 스크립트로 먼저 넣는다.
-- ============================================================================


-- ============================================================================
-- STEP 0. 사전 실측 — 이 인덱스가 필요한 규모인지 확인한다.
--         지역별 활성 공개 방이 수십 건 수준이면 서둘러 적용할 이유가 없다.
-- ============================================================================

SELECT region_id, COUNT(*) AS active_public_rooms
FROM chat_room
WHERE is_active = 1
  AND type IN ('GROUP', 'GROUP_STREET')
  AND region_id IS NOT NULL
GROUP BY region_id
ORDER BY active_public_rooms DESC
LIMIT 10;


-- ============================================================================
-- STEP 1. 현재 상태 확인
--         이름이 아니라 실제 정의로 판정한다. 같은 이름의 다른 인덱스도 걸러낸다.
--           DONE     — 건너뛴다
--           TODO     — STEP 2를 실행한다
--           MISMATCH — 같은 이름의 다른 인덱스가 있다. 자동 수정 금지.
--                      actual/expected를 비교해 사람이 재생성 여부를 판단한다.
-- ============================================================================

SELECT 'idx_chat_room_public_list' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 1
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index)
                = 'region_id,is_active,last_message_at,chat_room_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-') AS actual,
       'non_unique=1 cols=region_id,is_active,last_message_at,chat_room_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'chat_room'
  AND index_name = 'idx_chat_room_public_list';


-- ============================================================================
-- STEP 2. 인덱스 추가 (재실행해도 안전하다)
-- ============================================================================

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'chat_room'
                  AND index_name = 'idx_chat_room_public_list');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_chat_room_public_list ON chat_room (region_id, is_active, last_message_at, chat_room_id)',
    'SELECT "idx_chat_room_public_list 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 DONE인지 확인한다.
--
--         실행 계획도 함께 본다. key가 idx_chat_room_public_list이고
--         Extra에 filesort가 없어야 한다(Backward index scan이면 정상이다).
--         :region_id 자리에는 STEP 0에서 방이 가장 많은 지역을 넣는다.
-- ============================================================================

-- EXPLAIN SELECT r.chat_room_id
-- FROM chat_room r
-- WHERE r.region_id = :region_id
--   AND r.type IN ('GROUP', 'GROUP_STREET')
--   AND r.is_active = 1
--   AND (r.last_message_at < :cursor_last_message_at
--        OR (r.last_message_at = :cursor_last_message_at AND r.chat_room_id < :cursor_room_id)
--        OR r.last_message_at IS NULL)
-- ORDER BY r.last_message_at DESC, r.chat_room_id DESC
-- LIMIT 21;


-- ============================================================================
-- 롤백
--   메시지 수신 쓰기 지연이 관측되면 되돌린다. 공개 방 탐색은 전체 스캔 경로로 돌아갈 뿐
--   정상 동작한다. 되돌리려면 이 인덱스를 제거하면 된다 — 사람이 직접 실행한다.
--   되돌린 뒤에는 ChatRoomListIndexIntegrationTest가 실패한다(계획을 고정한 테스트다).
-- ============================================================================
