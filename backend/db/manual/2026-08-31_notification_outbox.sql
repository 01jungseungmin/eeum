-- ============================================================================
-- notification_outbox 신설 — 알림 생성의 유실 방지
--
-- 배경
--   알림 생성을 AFTER_COMMIT + @Async로 처리했다. 비동기 풀이 포화되면 그 작업이
--   버려져 알림이 아예 만들어지지 않았고, 원 요청(메시지 전송)은 성공으로 끝나
--   아무도 알아채지 못했다. 커밋된 사실과 그에 따른 알림 사이에 보장이 없었다.
--
--   이 테이블에 알림 요청을 원 트랜잭션과 함께 커밋하고, 스케줄러가 읽어서 처리한다.
--   풀이 포화되든 인스턴스가 죽든 기록은 남는다.
--
-- 중복 방지
--   상태 전이(DONE)를 알림 생성과 같은 트랜잭션에 묶는다. 처리 도중 죽으면 둘 다
--   롤백돼 다음 주기에 다시 시도한다 — 알림만 만들어지고 DONE을 못 남기는 창이 없다.
--
-- 보존
--   DONE은 24시간 뒤 정리한다(NotificationOutboxScheduler).
--   FAILED는 지우지 않는다 — 알림이 끝내 생성되지 않은 기록이라 조사 근거로 남긴다.
--
-- 인덱스
--   대기 행 조회가 유일한 조회 패턴이다: status로 좁히고 created_at 오름차순,
--   동률은 PK로 끊는다. 그 순서 그대로 인덱스를 만든다.
--
-- 적용 시점
--   ddl-auto=update가 만들어 주지만, 운영에서는 배포 전에 이 스크립트로 먼저 넣는다.
-- ============================================================================


-- ============================================================================
-- STEP 1. 현재 상태 확인
-- ============================================================================

SELECT 'notification_outbox' AS target,
       CASE WHEN COUNT(*) = 0 THEN 'TODO' ELSE 'DONE' END AS status
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'notification_outbox';

SELECT 'idx_outbox_status_created' AS target,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'status,created_at,outbox_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(GROUP_CONCAT(column_name ORDER BY seq_in_index), '-') AS actual,
       'status,created_at,outbox_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'notification_outbox'
  AND index_name = 'idx_outbox_status_created';


-- ============================================================================
-- STEP 2. 적용 — STEP 1이 TODO일 때만 실행한다
-- ============================================================================

CREATE TABLE notification_outbox (
    outbox_id     BIGINT       NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(100) NOT NULL,
    payload       LONGTEXT     NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    attempt_count INT          NOT NULL,
    last_error    VARCHAR(1000)    NULL,
    processed_at  DATETIME(6)      NULL,
    created_at    DATETIME(6)  NOT NULL,
    modified_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (outbox_id)
);

CREATE INDEX idx_outbox_status_created
    ON notification_outbox (status, created_at, outbox_id);


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 둘 다 DONE인지 확인한다
-- ============================================================================
