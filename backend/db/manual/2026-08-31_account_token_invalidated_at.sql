-- ============================================================================
-- account.token_invalidated_at 추가 — 토큰 회수의 최종 근거
--
-- 배경
--   토큰 회수를 Redis 삭제에만 의존했다. 세 경로로 새어나간다.
--     1) 삭제 작업이 비동기 풀을 타므로 포화 시 버려진다
--     2) 진행 중인 재발급이 삭제 직후 새 Refresh Token을 저장한다
--     3) 인스턴스 간 세션 종료 신호(Redis Pub/Sub)는 유실될 수 있다
--   제재했는데 세션이 살아 있고, 계정을 재활성화하면 그 토큰이 다시 동작한다.
--
--   이 컬럼은 제재와 같은 트랜잭션에서 커밋되므로 위 경로 어디에도 걸리지 않는다.
--   "이 시각 이전에 발급된 토큰은 전부 무효"라는 한 가지 사실로 덮는다.
--
-- 판정 위치
--   - JwtAuthenticationFilter  : 요청마다 Access Token의 iat 대조
--   - AuthService.reissue      : Refresh Token의 iat 대조
--   기록 위치
--   - AccountTokenInvalidationListener (BEFORE_COMMIT) — 원 트랜잭션과 함께 커밋
--
-- 비용
--   nullable BIGINT 하나. 기존 행은 NULL(=무효화 이력 없음)이라 판정에 영향이 없다.
--   조건 조회에 쓰이지 않으므로 인덱스는 필요 없다 — 계정 단건 조회 후 메모리에서 비교한다.
--
-- 적용 시점
--   ddl-auto=update가 컬럼을 만들어 주지만, 운영에서는 배포 전에 이 스크립트로 먼저 넣는다.
--   온라인 DDL(INSTANT)로 즉시 끝난다.
-- ============================================================================


-- ============================================================================
-- STEP 1. 현재 상태 확인
-- ============================================================================

SELECT 'account.token_invalidated_at' AS target,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(is_nullable) = 'YES' AND MAX(data_type) = 'datetime' THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT(MAX(data_type), ' nullable=', MAX(is_nullable)), '-') AS actual,
       'datetime nullable=YES' AS expected
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'account'
  AND column_name = 'token_invalidated_at';


-- ============================================================================
-- STEP 2. 적용 — STEP 1이 TODO일 때만 실행한다
-- ============================================================================

ALTER TABLE account ADD COLUMN token_invalidated_at DATETIME(6) NULL;


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 DONE인지 확인한다
-- ============================================================================
