-- ============================================================================
-- 채팅 메시지 목록 전용 인덱스 추가
--
-- 배경
--   GET /chat/rooms/{roomId}/messages는 방으로 좁히고 sent_at DESC, chat_message_id DESC로
--   커서 페이징한다. 그런데 chat_message에는 인덱스가 하나도 없었다 — Hibernate가 FK에
--   만들어 준 chat_room_id 인덱스로 방 전체를 훑은 뒤 정렬하는 셈이다.
--   메시지는 이 앱에서 가장 빨리 늘어나는 테이블이고, 이 조회는 채팅방을 열 때마다 돈다.
--
-- 컬럼 순서
--   등호 조건(chat_room_id)을 앞에, 정렬 키(sent_at, chat_message_id)를 뒤에 둔다.
--   PK를 명시하는 이유는 정렬에 tie-break가 포함되기 때문이다. 커서가
--   "sent_at이 같으면 chat_message_id로 끊는다"로 바뀌었으므로, 그 구간의 정렬까지
--   인덱스가 맡아야 filesort가 사라진다.
--
-- 함께 바뀐 계약
--   커서가 sent_at 하나에서 (sent_at, chat_message_id) 복합으로 바뀌었다. 예전에는
--   같은 시각에 저장된 메시지가 페이지 경계에 걸리면 나머지가 영구히 누락됐다.
--   이 인덱스는 그 새 정렬을 받쳐준다.
--
-- 비용
--   chat_message는 삽입 전용에 가깝다(수정·삭제가 드물다). 인덱스 하나가 늘면 INSERT마다
--   유지 비용이 붙지만, 조회가 전체 스캔에서 벗어나는 이득이 훨씬 크다.
--
-- 적용 시점
--   운영 반영은 서비스 영향이 적은 시간대에 한다. ALTER는 온라인 DDL(INPLACE)로 동작하지만
--   메시지 테이블은 이미 크기가 상당할 수 있어 시간이 걸린다. ddl-auto=update가 기존 테이블에
--   인덱스를 붙여줄지는 보장되지 않으므로(2026-08-25 런북의 경고 참고) 배포 전에 넣는다.
-- ============================================================================


-- ============================================================================
-- STEP 0. 사전 실측 — 규모와 현재 인덱스를 확인한다.
-- ============================================================================

SELECT COUNT(*) AS total_messages FROM chat_message;

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'chat_message'
GROUP BY index_name;


-- ============================================================================
-- STEP 1. 현재 상태 확인
--         이름이 아니라 실제 정의로 판정한다.
--           DONE     — 건너뛴다
--           TODO     — STEP 2를 실행한다
--           MISMATCH — 같은 이름의 다른 인덱스가 있다. 자동 수정 금지, 사람이 판단한다.
-- ============================================================================

SELECT 'idx_chat_message_room_sent' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 1
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index)
                = 'chat_room_id,sent_at,chat_message_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-') AS actual,
       'non_unique=1 cols=chat_room_id,sent_at,chat_message_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'chat_message'
  AND index_name = 'idx_chat_message_room_sent';


-- ============================================================================
-- STEP 2. 인덱스 추가 (재실행해도 안전하다)
-- ============================================================================

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'chat_message'
                  AND index_name = 'idx_chat_message_room_sent');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_chat_message_room_sent ON chat_message (chat_room_id, sent_at, chat_message_id)',
    'SELECT "idx_chat_message_room_sent 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 DONE인지 확인한다.
--
--         실행 계획도 함께 본다. key가 idx_chat_message_room_sent이고
--         Extra에 filesort가 없어야 한다(Backward index scan이면 정상이다).
--         :room_id 자리에는 메시지가 가장 많은 방을 넣는다.
-- ============================================================================

-- EXPLAIN SELECT m.chat_message_id
-- FROM chat_message m
-- WHERE m.chat_room_id = :room_id
--   AND (m.sent_at < :cursor_sent_at
--        OR (m.sent_at = :cursor_sent_at AND m.chat_message_id < :cursor_message_id))
-- ORDER BY m.sent_at DESC, m.chat_message_id DESC
-- LIMIT 51;


-- ============================================================================
-- 롤백
--   메시지 삽입 지연이 관측되면 되돌린다. 목록 조회는 전체 스캔 경로로 돌아갈 뿐
--   정상 동작한다. 되돌리려면 이 인덱스를 제거하면 된다 — 사람이 직접 실행한다.
-- ============================================================================
