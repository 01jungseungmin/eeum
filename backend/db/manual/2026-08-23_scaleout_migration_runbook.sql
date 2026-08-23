-- ============================================================================
-- 스케일아웃 배포용 스키마 마이그레이션 runbook
--
-- 이 프로젝트는 Flyway/Liquibase 없이 ddl-auto: update로 운영된다.
-- Hibernate의 스키마 갱신은 실패해도 로그만 남기고 부팅을 계속하므로,
-- 제약·인덱스가 조용히 안 걸린 채 배포가 끝날 수 있다. 그래서 수동으로 적용하고 검증한다.
--
-- MySQL DDL은 암묵적 커밋이라 트랜잭션으로 묶을 수 없다. 중간에 실패하면 반쪽 스키마가 남으므로,
-- 각 단계를 "이미 되어 있으면 건너뛸 수 있게" 만들고 재개 지점을 확인할 수 있게 구성했다.
--
-- ── 적용 대상 5개 ────────────────────────────────────────────────────────────
--   1) favorite.uk_favorite_account_ref            (UNIQUE)  중복 찜 차단
--   2) favorite.idx_favorite_ref                             대상별 카운트·통계·CASCADE 삭제
--   3) favorite.idx_favorite_account_type_created            내 찜 목록 정렬
--   4) used_product.idx_used_product_public_list             공개 목록 기본 조회
--   5) account.anonymized_at                       (COLUMN)  개인정보 파기 시각
--
-- ── 실행 순서 ────────────────────────────────────────────────────────────────
--   STEP 0 → STEP 1 → (중복 있으면 STEP 2) → STEP 3 → STEP 4 → STEP 5
--
-- ⚠ 쓰기 중단 구간: STEP 2 시작 ~ STEP 5 완료
--    중복을 지운 뒤 재계산까지 사이에 찜 등록·해제가 들어오면 카운트가 다시 어긋나고,
--    UNIQUE 제약 생성 전에 들어온 중복 요청은 그대로 통과한다.
--    중복이 0건이면(STEP 1 확인) 쓰기를 멈추지 않아도 된다 — STEP 3만 실행하면 된다.
--
-- ⚠ 5번(anonymized_at)이 없으면 탈퇴 계정 정리 스케줄러가 매일 실패한다.
--    findWithdrawnAccountsBefore 쿼리가 이 컬럼을 참조한다.
-- ============================================================================


-- ============================================================================
-- STEP 0. 사전 실측 — 스키마를 바꾸기 전에 데이터 상태를 파악한다
-- ============================================================================

-- 0-1. 중복 찜 (STEP 1 UNIQUE 생성의 전제조건)
SELECT account_id, ref_type, ref_id, COUNT(*) AS dup_count
FROM favorite
GROUP BY account_id, ref_type, ref_id
HAVING dup_count > 1;

-- 0-2. dangling 찜 — 대상이 사라진 찜 (제약과 무관하나 정합성 확인용)
SELECT 'STORE' AS ref_type, COUNT(*) AS dangling
FROM favorite f
WHERE f.ref_type = 'STORE'
  AND NOT EXISTS (SELECT 1 FROM store s WHERE s.store_id = f.ref_id)
UNION ALL
SELECT 'USED_PRODUCT', COUNT(*)
FROM favorite f
WHERE f.ref_type = 'USED_PRODUCT'
  AND NOT EXISTS (SELECT 1 FROM used_product p WHERE p.used_product_id = f.ref_id);

-- 0-3. 대표 이미지 중복·부재 (사진이 있는데 대표가 0개 또는 2개 이상)
SELECT used_product_id, SUM(is_thumbnail) AS thumbnail_count
FROM used_product_image
GROUP BY used_product_id
HAVING thumbnail_count <> 1;

-- 0-4. favoriteCount와 실제 찜 수의 불일치 규모
SELECT COUNT(*) AS mismatched_used_products
FROM used_product p
WHERE p.favorite_count <> (
    SELECT COUNT(*) FROM favorite f
    WHERE f.ref_type = 'USED_PRODUCT' AND f.ref_id = p.used_product_id
);


-- ============================================================================
-- STEP 1. 현재 상태 확인 — 재개 지점
--         중단 후 다시 시작할 때 이 쿼리만 돌리면 무엇이 남았는지 알 수 있다.
--         DONE인 항목은 건너뛴다.
-- ============================================================================

SELECT 'uk_favorite_account_ref' AS object_name,
       IF(COUNT(*) > 0, 'DONE', 'TODO') AS status
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'uk_favorite_account_ref'
UNION ALL
SELECT 'idx_favorite_ref',
       IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_ref'
UNION ALL
SELECT 'idx_favorite_account_type_created',
       IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_account_type_created'
UNION ALL
SELECT 'idx_used_product_public_list',
       IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'used_product'
  AND index_name = 'idx_used_product_public_list'
UNION ALL
SELECT 'account.anonymized_at',
       IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'account'
  AND column_name = 'anonymized_at';


-- ============================================================================
-- STEP 2. 중복 찜 정리 — STEP 0-1이 0행이면 건너뛴다
--         여기서부터 STEP 5까지 찜 쓰기를 중단한다.
-- ============================================================================

-- 2-1. 지워질 행을 먼저 눈으로 확인한다 (DELETE 전에 반드시 실행)
SELECT f.*
FROM favorite f
JOIN (
    SELECT MIN(favorite_id) AS keep_id, account_id, ref_type, ref_id
    FROM favorite
    GROUP BY account_id, ref_type, ref_id
    HAVING COUNT(*) > 1
) d
  ON f.account_id = d.account_id
 AND f.ref_type   = d.ref_type
 AND f.ref_id     = d.ref_id
 AND f.favorite_id <> d.keep_id;

-- 2-2. 같은 대상에서 favorite_id가 가장 작은 행만 남기고 삭제
DELETE f FROM favorite f
JOIN (
    SELECT MIN(favorite_id) AS keep_id, account_id, ref_type, ref_id
    FROM favorite
    GROUP BY account_id, ref_type, ref_id
    HAVING COUNT(*) > 1
) d
  ON f.account_id = d.account_id
 AND f.ref_type   = d.ref_type
 AND f.ref_id     = d.ref_id
 AND f.favorite_id <> d.keep_id;

-- 2-3. 재확인 — 반드시 0행이어야 STEP 3이 성공한다
SELECT account_id, ref_type, ref_id, COUNT(*) AS dup_count
FROM favorite
GROUP BY account_id, ref_type, ref_id
HAVING dup_count > 1;


-- ============================================================================
-- STEP 3. 스키마 변경
--         MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않는다.
--         STEP 1에서 TODO인 것만 골라 실행한다.
--         (전체를 그대로 붙여 넣고 싶으면 아래 STEP 3-ALT를 쓴다)
-- ============================================================================

-- 3-1. 중복 찜 차단. STEP 2-3이 0행이 아니면 여기서 실패한다.
ALTER TABLE favorite
    ADD CONSTRAINT uk_favorite_account_ref UNIQUE (account_id, ref_type, ref_id);

-- 3-2. 대상 기준 조회 전용.
--      UNIQUE 인덱스는 account_id가 선행 컬럼이라 (ref_type, ref_id) 조회에 쓰이지 못한다.
CREATE INDEX idx_favorite_ref ON favorite (ref_type, ref_id);

-- 3-3. 내 찜 목록(타입별 + 등록 최신순 + PK tie-break).
--      UNIQUE 인덱스는 세 번째 컬럼이 ref_id라 created_at 정렬에 쓰이지 못한다.
CREATE INDEX idx_favorite_account_type_created
    ON favorite (account_id, ref_type, created_at, favorite_id);

-- 3-4. 중고 공개 목록 기본 조회(지역 + 삭제·숨김 제외 + 최신순).
--      기존 idx_used_product_region_status는 status가 선행이라
--      상태 필터가 없으면 created_at까지 닿지 못한다.
--      ⚠ 운영 데이터로 EXPLAIN 확인 후 적용 여부를 판단할 것.
CREATE INDEX idx_used_product_public_list
    ON used_product (region_id, deleted_at, is_hidden, created_at);

-- 3-5. 개인정보 파기 시각.
--      이 컬럼이 없으면 탈퇴 계정 정리 스케줄러가 매일 실패한다.
ALTER TABLE account ADD COLUMN anonymized_at DATETIME(6) NULL;


-- ============================================================================
-- STEP 3-ALT. 조건부 실행 (이미 적용된 항목을 건너뛴다)
--             STEP 3을 하나씩 고르는 대신 전체를 붙여 넣고 싶을 때 사용한다.
--             재실행해도 안전하다.
-- ============================================================================

-- 3-ALT-1. UNIQUE
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'favorite'
                  AND index_name = 'uk_favorite_account_ref');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE favorite ADD CONSTRAINT uk_favorite_account_ref UNIQUE (account_id, ref_type, ref_id)',
    'SELECT "uk_favorite_account_ref 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3-ALT-2. idx_favorite_ref
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'favorite'
                  AND index_name = 'idx_favorite_ref');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_favorite_ref ON favorite (ref_type, ref_id)',
    'SELECT "idx_favorite_ref 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3-ALT-3. idx_favorite_account_type_created
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'favorite'
                  AND index_name = 'idx_favorite_account_type_created');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_favorite_account_type_created ON favorite (account_id, ref_type, created_at, favorite_id)',
    'SELECT "idx_favorite_account_type_created 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3-ALT-4. idx_used_product_public_list
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'used_product'
                  AND index_name = 'idx_used_product_public_list');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_used_product_public_list ON used_product (region_id, deleted_at, is_hidden, created_at)',
    'SELECT "idx_used_product_public_list 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3-ALT-5. account.anonymized_at
SET @exists := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account'
                  AND column_name = 'anonymized_at');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE account ADD COLUMN anonymized_at DATETIME(6) NULL',
    'SELECT "account.anonymized_at 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 4. 검증 — 5개가 모두 DONE이어야 한다
--         STEP 1과 같은 쿼리다. 여기서 TODO가 남으면 STEP 3으로 돌아간다.
-- ============================================================================

SELECT 'uk_favorite_account_ref' AS object_name,
       IF(COUNT(*) > 0, 'DONE', 'TODO') AS status
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'uk_favorite_account_ref'
UNION ALL
SELECT 'idx_favorite_ref', IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_ref'
UNION ALL
SELECT 'idx_favorite_account_type_created', IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'idx_favorite_account_type_created'
UNION ALL
SELECT 'idx_used_product_public_list', IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'used_product'
  AND index_name = 'idx_used_product_public_list'
UNION ALL
SELECT 'account.anonymized_at', IF(COUNT(*) > 0, 'DONE', 'TODO')
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'account'
  AND column_name = 'anonymized_at';

-- UNIQUE 제약이 실제로 UNIQUE인지 확인 (NON_UNIQUE = 0 이어야 한다)
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'favorite'
  AND index_name = 'uk_favorite_account_ref'
ORDER BY seq_in_index;


-- ============================================================================
-- STEP 5. 사후 처리 — 카운트 재계산
--         STEP 2에서 중복을 지웠다면 favoriteCount가 실제 행 수와 어긋난다.
--         애플리케이션 배포 후 관리자 API로 재계산한다.
--
--           POST /admin/favorites/recalculate                      (상점 + 중고 게시글)
--           POST /admin/favorites/recalculate?refType=USED_PRODUCT (중고만)
--
--         WHERE 없는 전체 UPDATE라 트래픽이 적은 시간대에 실행한다.
--         재계산이 끝나면 쓰기 중단을 해제한다.
-- ============================================================================

-- 5-1. 재계산 후 확인 — 0이어야 한다
SELECT COUNT(*) AS mismatched_used_products
FROM used_product p
WHERE p.favorite_count <> (
    SELECT COUNT(*) FROM favorite f
    WHERE f.ref_type = 'USED_PRODUCT' AND f.ref_id = p.used_product_id
);

SELECT COUNT(*) AS mismatched_stores
FROM store s
WHERE s.favorite_count <> (
    SELECT COUNT(*) FROM favorite f
    WHERE f.ref_type = 'STORE' AND f.ref_id = s.store_id
);
