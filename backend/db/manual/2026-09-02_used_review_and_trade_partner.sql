-- ============================================================================
-- 중고거래 후기 — used_review 신설 + used_product.buyer_account_id 추가
--
-- 배경
--   후기를 쓸 자격의 근거가 필요했다. "거래가 완료됐다"만으로는 부족하다 —
--   그 거래의 상대가 누구인지 알 수 없으면 아무나 후기를 쓸 수 있다.
--   그래서 판매자가 예약·판매완료 시 지정하는 거래 상대를 used_product에 두고,
--   후기는 그 상대만 쓸 수 있게 한다(UsedProduct.isPurchasedBy).
--
--   buyer_account_id는 NULL을 허용한다. 앱 밖에서 성사된 거래를 판매완료로 정리하거나
--   상대 없이 "예약중"만 표시하는 경우가 있어, 지정을 강제하면 그런 글을 SOLD로 만들 수 없다.
--   그 거래에는 후기가 붙지 않는다.
--
-- 중복 방지
--   uk_used_review_product_reviewer가 최종 방어선이다. 앱의 존재 확인만으로는
--   확인과 INSERT 사이에 끼어든 동시 요청을 막지 못한다(UsedReviewService.create가
--   유니크 위반을 USED_REVIEW_ALREADY_EXISTS로 변환한다).
--   작성자를 키에 포함해 두면 나중에 상호 평가로 넓힐 때 제약을 바꾸지 않아도 된다.
--
-- 삭제 정책
--   후기는 Soft Delete 대상이 아니다(삭제하면 물리 삭제). 게시글과 달리 다른 도메인이
--   후기를 참조하지 않는다. 반대로 게시글이 Soft Delete돼도 후기는 남는다 —
--   판매완료 글에 후기가 매달려 있다는 것이 UsedProduct를 Soft Delete로 둔 이유다.
--   그래서 used_review의 FK에 연쇄 삭제를 걸지 않는다. 게시글 물리 삭제가 일어나지 않는 것이
--   전제이고, 연쇄를 걸면 그 전제가 깨졌을 때 판매자 평판이 조용히 사라진다.
--
-- 적용 시점
--   ddl-auto=update가 테이블과 컬럼을 만들어 주지만, 운영에서는 배포 전에 이 스크립트로
--   먼저 넣는다. FK·UNIQUE 이름을 사람이 읽을 수 있게 고정하는 것도 목적이다
--   (자동 생성 이름은 FKc9e2yutbo29yyv7m8q19aklvn 형태라 장애 시 추적이 어렵다).
-- ============================================================================


-- ============================================================================
-- STEP 1. 현재 상태 확인
--         이름이 아니라 실제 정의로 판정한다.
--           DONE     — 건너뛴다
--           TODO     — STEP 2를 실행한다
--           MISMATCH — 같은 이름의 다른 정의가 있다. 자동 수정 금지, 사람이 판단한다.
-- ============================================================================

SELECT 'used_review(table)' AS object_name,
       CASE WHEN COUNT(*) = 0 THEN 'TODO' ELSE 'DONE' END AS status
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'used_review';

SELECT 'uk_used_review_product_reviewer' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 0
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'used_product_id,reviewer_account_id'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-') AS actual,
       'non_unique=0 cols=used_product_id,reviewer_account_id' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'used_review'
  AND index_name = 'uk_used_review_product_reviewer';

SELECT 'idx_used_review_product' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'used_product_id,created_at'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(GROUP_CONCAT(column_name ORDER BY seq_in_index), '-') AS actual,
       'used_product_id,created_at' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'used_review'
  AND index_name = 'idx_used_review_product';

SELECT 'idx_used_review_reviewer' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'reviewer_account_id,created_at'
               THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(GROUP_CONCAT(column_name ORDER BY seq_in_index), '-') AS actual,
       'reviewer_account_id,created_at' AS expected
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'used_review'
  AND index_name = 'idx_used_review_reviewer';

SELECT 'used_product.buyer_account_id' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(is_nullable) = 'YES' AND MAX(data_type) = 'bigint' THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT('type=', MAX(data_type), ' nullable=', MAX(is_nullable)), '-') AS actual,
       'type=bigint nullable=YES' AS expected
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'used_product'
  AND column_name = 'buyer_account_id';

SELECT 'fk_used_product_buyer' AS object_name,
       CASE WHEN COUNT(*) = 0 THEN 'TODO' ELSE 'DONE' END AS status
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name = 'used_product'
  AND constraint_name = 'fk_used_product_buyer' AND constraint_type = 'FOREIGN KEY';


-- ============================================================================
-- STEP 2. 적용 — STEP 1이 TODO인 항목만 실행된다 (재실행해도 안전하다)
-- ============================================================================

-- 2-1. 후기 테이블
CREATE TABLE IF NOT EXISTS used_review (
    used_review_id      BIGINT      NOT NULL AUTO_INCREMENT,
    used_product_id     BIGINT      NOT NULL,
    reviewer_account_id BIGINT      NOT NULL,
    rating              INT         NOT NULL,
    content             TEXT        NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    modified_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (used_review_id),
    CONSTRAINT uk_used_review_product_reviewer
        UNIQUE (used_product_id, reviewer_account_id),
    CONSTRAINT fk_used_review_product
        FOREIGN KEY (used_product_id) REFERENCES used_product (used_product_id),
    CONSTRAINT fk_used_review_reviewer
        FOREIGN KEY (reviewer_account_id) REFERENCES account (account_id)
);

-- 판매자별 후기 목록은 상품을 거쳐 판매자에 매달린다 — 조인 기준 컬럼을 잡아둔다.
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'used_review'
                  AND index_name = 'idx_used_review_product');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_used_review_product ON used_review (used_product_id, created_at)',
    'SELECT "idx_used_review_product 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 내가 쓴 후기 목록
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'used_review'
                  AND index_name = 'idx_used_review_reviewer');
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_used_review_reviewer ON used_review (reviewer_account_id, created_at)',
    'SELECT "idx_used_review_reviewer 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2-2. 거래 상대 컬럼 — 기존 행은 NULL이다(지난 거래에는 상대 기록이 없다).
--      백필하지 않는다. 임의로 채우면 그 계정에 후기 작성 권한이 생긴다.
SET @exists := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'used_product'
                  AND column_name = 'buyer_account_id');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE used_product ADD COLUMN buyer_account_id BIGINT NULL',
    'SELECT "used_product.buyer_account_id 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'used_product'
                  AND constraint_name = 'fk_used_product_buyer'
                  AND constraint_type = 'FOREIGN KEY');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE used_product ADD CONSTRAINT fk_used_product_buyer FOREIGN KEY (buyer_account_id) REFERENCES account (account_id)',
    'SELECT "fk_used_product_buyer 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 3. 검증 — STEP 1을 다시 실행해 6개 항목이 모두 DONE인지 확인한다.
--
--         정합성도 함께 본다. 아래 두 쿼리는 0건이어야 한다.
-- ============================================================================

-- 판매자가 자기 글의 구매자로 지정된 경우 — 자기 거래에 후기를 남기는 경로가 열린다.
-- 앱은 UsedProduct.assertNotSeller로 막지만, 수동 DB 수정이 있었다면 여기서 드러난다.
SELECT p.used_product_id, p.account_id AS seller, p.buyer_account_id AS buyer
FROM used_product p
WHERE p.buyer_account_id IS NOT NULL
  AND p.buyer_account_id = p.account_id;

-- 후기 자격과 어긋난 행 — 후기는 SOLD이고 지정 구매자 본인이 쓴 것만 존재해야 한다.
SELECT r.used_review_id, p.used_product_id, p.status, p.buyer_account_id, r.reviewer_account_id
FROM used_review r
JOIN used_product p ON p.used_product_id = r.used_product_id
WHERE p.status <> 'SOLD'
   OR p.buyer_account_id IS NULL
   OR p.buyer_account_id <> r.reviewer_account_id;

-- FK 중복 확인 — 배포 후 한 번 본다.
--   이 스크립트는 FK에 사람이 읽을 수 있는 이름을 붙이는데, ddl-auto=update가 같은 컬럼에
--   자기 이름(FK로 시작하는 해시)으로 FK를 하나 더 만들 수 있다. 동작에는 문제가 없지만
--   같은 제약이 둘이면 이후 스키마 변경에서 혼란이 생긴다.
--   컬럼당 1건이어야 한다. 2건 이상이면 자동 생성된 쪽을 사람이 제거한다.
SELECT k.table_name, k.column_name, COUNT(*) AS fk_count,
       GROUP_CONCAT(k.constraint_name ORDER BY k.constraint_name) AS constraints
FROM information_schema.key_column_usage k
JOIN information_schema.referential_constraints rc
  ON rc.constraint_schema = k.constraint_schema
 AND rc.constraint_name = k.constraint_name
WHERE k.table_schema = DATABASE()
  AND ((k.table_name = 'used_review' AND k.column_name IN ('used_product_id', 'reviewer_account_id'))
    OR (k.table_name = 'used_product' AND k.column_name = 'buyer_account_id'))
GROUP BY k.table_name, k.column_name;


-- ============================================================================
-- 롤백
--   후기 기능 자체를 되돌릴 때만 한다. used_review를 제거하면 판매자 평판이 함께 사라지므로
--   먼저 백업한다. 되돌리는 순서는 FK → 컬럼 → 테이블이다.
--   운영에서 실행할 일이 아니라 판단 근거로만 남긴다 — 필요하면 사람이 직접 작성해 실행한다.
-- ============================================================================
