-- ============================================================================
-- 찜 전체 목록(/favorites/me/all) 전용 인덱스 추가
--
-- 배경
--   GET /favorites/me/all은 account_id만 등호로 걸고 created_at DESC, favorite_id DESC로
--   정렬한다. 기존 idx_favorite_account_type_created는 두 번째 컬럼이 ref_type이라,
--   account_id만 고정하면 그 안이 ref_type 순으로 정렬돼 이 정렬에 쓰이지 못한다.
--
-- 실측 (MySQL 8.0, 한 계정에 찜 2000건, LIMIT 21)
--   추가 전: key=uk_favorite_account_ref  rows=2000  Extra=Using filesort
--            → 옵티마이저가 UNIQUE 인덱스를 고르고, 해당 계정의 전체 행을 읽어 정렬한다.
--              페이지 하나를 그리는 비용이 그 사용자의 찜 개수에 비례한다.
--   추가 후: key=idx_favorite_account_created  Extra=Backward index scan; Using index
--            EXPLAIN ANALYZE: actual rows=21, 0.105ms — LIMIT만큼만 읽는다.
--
--   즉 정확성 문제가 아니라 비용 곡선 문제다. 찜이 수십 건인 사용자에게는 차이가 없고,
--   수천 건인 사용자에게는 무한 스크롤 매 페이지마다 전체 스캔이 반복된다.
--
-- 비용
--   favorite는 토글 쓰기 테이블이라 인덱스가 하나 늘면 INSERT/DELETE마다 유지 비용이 붙는다.
--   다만 찜 토글은 사용자 행동당 1회로 빈도가 낮고, 기존 인덱스가 이미 3개다.
--
-- 적용 시점
--   운영 반영은 서비스 영향이 적은 시간대에 한다. ALTER는 온라인 DDL(INPLACE)로 동작하지만
--   테이블 크기에 따라 시간이 걸린다. 먼저 STEP 1로 현재 상태를 확인한다.
-- ============================================================================


-- ============================================================================
-- STEP 0. 사전 실측 — 이 인덱스가 필요한 규모인지 확인한다
--         상위 사용자의 찜 개수가 수백 건 미만이면 서둘러 적용할 이유가 없다.
-- ============================================================================

SELECT account_id, COUNT(*) AS favorite_count
FROM favorite
GROUP BY account_id
ORDER BY favorite_count DESC
LIMIT 10;


-- ============================================================================
-- STEP 1. 현재 상태 확인
--         이름이 아니라 실제 정의로 판정한다. 같은 이름의 다른 인덱스도 걸러낸다.
--           DONE     — 건너뛴다
--           TODO     — STEP 2를 실행한다
--           MISMATCH — 같은 이름의 다른 인덱스가 있다. 자동 수정 금지.
--                      actual/expected를 비교해 사람이 DROP 후 재생성할지 판단한다.
-- ============================================================================

SELECT 'idx_favorite_account_created' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 1
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'account_id,created_at,favorite_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-') AS actual,
       'non_unique=1 cols=account_id,created_at,favorite_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_account_created';


-- ============================================================================
-- STEP 2. 인덱스 추가 (재실행해도 안전하다)
-- ============================================================================

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'favorite'
                  AND index_name = 'idx_favorite_account_created');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_favorite_account_created ON favorite (account_id, created_at, favorite_id)',
    'SELECT "idx_favorite_account_created 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 3. 검증 — STEP 1과 같은 쿼리다. DONE이어야 한다.
--         실행 계획도 함께 본다. key가 idx_favorite_account_created이고
--         Extra에 filesort가 없어야 한다.
--         :account_id 자리에는 STEP 0에서 찾은 찜이 가장 많은 계정을 넣는다.
-- ============================================================================

SELECT 'idx_favorite_account_created' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 1
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'account_id,created_at,favorite_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-') AS actual,
       'non_unique=1 cols=account_id,created_at,favorite_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_account_created';

-- EXPLAIN ANALYZE SELECT f.favorite_id FROM favorite f
-- WHERE f.account_id = :account_id
-- ORDER BY f.created_at DESC, f.favorite_id DESC
-- LIMIT 21;


-- ============================================================================
-- 롤백 — 쓰기 지연이 관측되면 되돌린다. 목록 조회는 filesort 경로로 돌아갈 뿐 정상 동작한다.
-- ============================================================================

-- DROP INDEX idx_favorite_account_created ON favorite;
