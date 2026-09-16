-- ============================================================================
-- account.token_version 추가 — 토큰 회수의 최종 근거
--
-- 배경
--   토큰 회수를 Redis 삭제에만 의존했다. 세 경로로 새어나간다.
--     1) 삭제 작업이 비동기 풀을 타므로 포화 시 버려진다
--     2) 진행 중인 발급이 삭제 직후 새 토큰을 저장한다
--     3) 인스턴스 간 세션 종료 신호(Redis Pub/Sub)는 유실될 수 있다
--
--   발급 시점의 세대를 JWT `ver` claim에 박고, 검증 때 계정의 현재 세대와 대조한다.
--   세대는 제재와 같은 트랜잭션에서 오르므로 위 경로 어디에도 걸리지 않는다.
--
-- 왜 시각이 아니라 정수인가
--   시각(iat 대조)으로 만들었다가 세 가지 문제로 교체했다.
--     - iat가 초 단위라 같은 초에 발급된 토큰을 구분할 수 없다
--     - 인스턴스마다 timezone이 다르면 같은 DB 값의 판정이 갈린다
--     - 제재 직후 발급된 토큰이 "더 늦은 시각"이라 통과한다
--   세대 번호는 셋 다 겪지 않는다. 발급 때 읽은 값이 낡았으면 그 토큰이 낡은 것이다.
--
-- backfill이 필요 없는 이유
--   이 기능 도입 전에 발급된 토큰에는 `ver` claim이 아예 없고, 검증은 claim 없음을
--   무효로 다룬다. 배포 시점에 살아 있던 모든 토큰이 한 번에 무효가 되므로
--   기존 SUSPENDED/WITHDRAWN 계정을 따로 채울 필요가 없다.
--
--   대가는 배포 직후 전 사용자가 한 번 재로그인해야 한다는 것이다.
--   앱 배포 전이라 지금이 이 비용을 치르기 가장 싼 시점이다.
--
-- 판정·기록 위치
--   기록 : AccountTokenInvalidationListener (BEFORE_COMMIT) — 원 트랜잭션과 함께 커밋
--   판정 : JwtAuthenticationFilter(Access) / AuthService.reissue(Refresh)
--          StompAuthChannelInterceptor(WebSocket CONNECT)
--          AccountService(ReAuth) / AuthService.resetPassword(Password Reset)
--          WebSocketSessionReconciliationScheduler(붙어 있는 세션 주기 대조)
--
-- 비용
--   BIGINT NOT NULL DEFAULT 0. 조건 조회에 쓰이지 않으므로 인덱스는 필요 없다 —
--   계정 단건/IN 조회 후 메모리에서 비교한다.
--
-- 적용 시점
--   ddl-auto=update가 만들어 주지만, 운영에서는 배포 전에 이 스크립트로 먼저 넣는다.
--   온라인 DDL(INSTANT)로 즉시 끝난다.
-- ============================================================================


-- ============================================================================
-- STEP 1. 현재 상태 확인
-- ============================================================================

SELECT 'account.token_version' AS target,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(is_nullable) = 'NO'
                AND MAX(data_type) = 'bigint'
                AND MAX(IFNULL(column_default, '')) = '0' THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT(MAX(data_type),
                     ' nullable=', MAX(is_nullable),
                     ' default=', IFNULL(MAX(column_default), 'NULL')), '-') AS actual,
       'bigint nullable=NO default=0' AS expected
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'account'
  AND column_name = 'token_version';


-- ============================================================================
-- STEP 2. 적용 — STEP 1이 TODO일 때만 실행한다
-- ============================================================================

ALTER TABLE account ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 DONE인지 확인한다
--
--   MISMATCH면 정의를 직접 확인한다.
--     SHOW CREATE TABLE account;
-- ============================================================================
