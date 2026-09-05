-- ============================================================================
-- 중고거래 1:1 문의방 — chat_room 스키마 변경
--
-- 배경
--   "상품 + 구매자"당 ACTIVE 문의방을 1개로 제한한다. 종료된 방은 같은 조합으로 얼마든지
--   누적될 수 있어야 하므로 단순 UNIQUE(ref_id, buyer_account_id, is_active)로는 안 된다.
--   기존 가게 단톡방과 같은 방식으로 active_ref_key 생성 컬럼을 확장한다.
--
--     STORE        → 'STORE:{storeId}'
--     USED_PRODUCT → 'USED_PRODUCT:{usedProductId}:{buyerAccountId}'
--
--   종료됐거나 해당 refType이 아니면 NULL이 되고, MySQL UNIQUE는 NULL 중복을 허용한다.
--
-- ⚠️ ddl-auto=update로는 반영되지 않는다
--   active_ref_key는 MySQL 생성 컬럼이고, ddl-auto는 "컬럼 신규 생성"만 하며 기존 컬럼 정의를
--   MODIFY하지 않는다. 즉 앱만 배포하면 생성식은 STORE 전용인 채로 남고, 중고 문의방 중복이
--   DB 레벨에서 막히지 않는다(Redis 락이 유실되면 그대로 중복 생성).
--   반드시 이 스크립트를 적용하고 STEP 3으로 실제 정의를 확인한다.
--
-- 적용 순서
--   STEP 0 확인 → STEP 1 컬럼 추가 → STEP 2 생성식 교체 → STEP 3 검증
-- ============================================================================


-- ============================================================================
-- STEP 0. 사전 확인 — 유니크 인덱스를 다시 만들기 전에 위반 데이터가 없어야 한다
--         ddl-auto는 유니크 인덱스 생성 실패를 로그만 남기고 넘어가므로 사전 확인이 필수다.
-- ============================================================================

-- 0-1. 기존 가게 단톡방 중복 (생성식에 type이 없으므로 ref_id만으로 센다)
SELECT ref_id, COUNT(*) AS cnt FROM chat_room
WHERE is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
GROUP BY ref_id HAVING cnt > 1;

-- 0-2. 현재 active_ref_key 정의 확인 (교체 전 원본 보존용)
SHOW CREATE TABLE chat_room;


-- ============================================================================
-- STEP 1. buyer_account_id 추가
--         FK를 걸지 않는다 — ref_id와 같은 정책이고, 계정 물리 삭제가 chat_room FK에
--         막히는 기존 문제를 키우지 않기 위해서다.
-- ============================================================================

SET @exists := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'chat_room'
                  AND column_name = 'buyer_account_id');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE chat_room ADD COLUMN buyer_account_id BIGINT NULL',
    'SELECT "chat_room.buyer_account_id 이미 존재 — 건너뜀"');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================================
-- STEP 2. active_ref_key 생성식 교체
--         생성 컬럼은 MODIFY로 식을 바꿀 수 없어 DROP 후 재생성한다.
--         이 구간에는 유니크 방어가 없으므로 쓰기가 적은 시간대에 수행한다.
--         (STEP 1을 먼저 실행해야 한다 — 생성식이 buyer_account_id를 참조한다)
-- ============================================================================

ALTER TABLE chat_room DROP INDEX uk_chat_room_active_ref;
ALTER TABLE chat_room DROP COLUMN active_ref_key;

ALTER TABLE chat_room ADD COLUMN active_ref_key VARCHAR(80)
    GENERATED ALWAYS AS (CASE
        WHEN is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
            THEN CONCAT(ref_type, ':', ref_id)
        WHEN is_active = 1 AND ref_type = 'USED_PRODUCT' AND ref_id IS NOT NULL
             AND buyer_account_id IS NOT NULL
            THEN CONCAT(ref_type, ':', ref_id, ':', buyer_account_id)
    END) STORED;

ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_active_ref UNIQUE (active_ref_key);


-- ============================================================================
-- STEP 3. 검증 — 이름이 아니라 실제 정의로 판정한다
--           DONE     — 반영 완료
--           TODO     — STEP 1~2를 실행한다
--           MISMATCH — 같은 이름의 다른 정의가 있다. 자동 수정 금지, 사람이 판단한다.
-- ============================================================================

SELECT 'chat_room.buyer_account_id' AS object_name,
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(column_type) = 'bigint' AND MAX(is_nullable) = 'YES' THEN 'DONE'
           ELSE 'MISMATCH'
       END AS status,
       IFNULL(CONCAT(MAX(column_type), ' nullable=', MAX(is_nullable)), '-') AS actual,
       'bigint nullable=YES' AS expected
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'chat_room'
  AND column_name = 'buyer_account_id'
UNION ALL
SELECT 'active_ref_key(USED_PRODUCT 분기)',
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(generation_expression) LIKE '%USED_PRODUCT%'
            AND MAX(generation_expression) LIKE '%buyer_account_id%' THEN 'DONE'
           ELSE 'MISMATCH'
       END,
       IFNULL(LEFT(MAX(generation_expression), 60), '-'),
       'CASE ... USED_PRODUCT ... buyer_account_id ...'
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'chat_room'
  AND column_name = 'active_ref_key'
UNION ALL
SELECT 'uk_chat_room_active_ref',
       CASE
           WHEN COUNT(*) = 0 THEN 'TODO'
           WHEN MAX(non_unique) = 0
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'active_ref_key' THEN 'DONE'
           ELSE 'MISMATCH'
       END,
       IFNULL(CONCAT('non_unique=', MAX(non_unique),
                     ' cols=', GROUP_CONCAT(column_name ORDER BY seq_in_index)), '-'),
       'non_unique=0 cols=active_ref_key'
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'chat_room'
  AND index_name = 'uk_chat_room_active_ref';

-- 생성식 원문 육안 확인 (위 판정과 함께 본다)
SHOW CREATE TABLE chat_room;


-- ============================================================================
-- 롤백 — 중고 문의방 기능을 되돌릴 때. 생성식을 STORE 전용으로 되돌린다.
--        buyer_account_id는 남겨도 무해하므로 지우지 않는다(데이터 보존).
-- ============================================================================

-- ALTER TABLE chat_room DROP INDEX uk_chat_room_active_ref;
-- ALTER TABLE chat_room DROP COLUMN active_ref_key;
-- ALTER TABLE chat_room ADD COLUMN active_ref_key VARCHAR(80)
--     GENERATED ALWAYS AS (CASE WHEN is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
--                          THEN CONCAT(ref_type, ':', ref_id) END) STORED;
-- ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_active_ref UNIQUE (active_ref_key);
