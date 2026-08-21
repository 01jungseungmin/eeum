-- ============================================================================
-- favorite 테이블: 중복 찜 차단 UNIQUE 제약 + 대상 조회 인덱스
--
-- 배경
--   서비스 코드는 UNIQUE(account_id, ref_type, ref_id)를 전제로 동작한다.
--     - FavoriteService.addFavorite: DataIntegrityViolationException을 잡아
--       FAVORITE_ALREADY_EXISTS로 변환한다.
--     - findByAccount_AccountIdAndRefTypeAndRefId: Optional 반환이라 중복 행이 있으면
--       IncorrectResultSizeDataAccessException으로 실패한다.
--     - FavoriteService.deleteAllByAccountId: 대상 ID를 IN 절로 한 번에 감소시키므로
--       중복 행이 있으면 감소량이 실제 찜 수와 어긋난다.
--   그런데 제약이 엔티티에도 DB에도 없었다. 엔티티에는 추가했지만, 이 프로젝트는
--   ddl-auto: update로 운영되고 Hibernate는 인덱스 생성에 실패해도 로그만 남기고
--   부팅을 계속한다. 즉 기존 중복 데이터가 있으면 제약이 조용히 안 걸린 채 배포가 끝난다.
--
-- 실행 순서: 1 → (중복이 있으면) 2 → 3 → 4 → 5
-- 서비스 배포 전에 실행할 것.
--
-- ⚠ 2~5 구간에는 찜 쓰기를 중단해야 한다.
--   중복을 지운 뒤 재계산까지 사이에 찜 등록·해제가 들어오면 카운트가 다시 어긋나고,
--   제약 생성 전에 들어온 중복 요청은 그대로 통과한다.
-- ============================================================================

-- 1) 중복 확인. 결과가 0행이면 2·3을 건너뛰고 4로 간다.
SELECT account_id, ref_type, ref_id, COUNT(*) AS dup_count
FROM favorite
GROUP BY account_id, ref_type, ref_id
HAVING dup_count > 1;

-- 2) 중복 정리. 같은 (account_id, ref_type, ref_id)에서 favorite_id가 가장 작은 행만 남긴다.
--    먼저 남길 행을 눈으로 확인하려면 DELETE를 SELECT f.* 로 바꿔 실행한다.
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

-- 3) 정리 결과 재확인. 반드시 0행이어야 한다.
SELECT account_id, ref_type, ref_id, COUNT(*) AS dup_count
FROM favorite
GROUP BY account_id, ref_type, ref_id
HAVING dup_count > 1;

-- 4) 제약·인덱스 생성. 3이 0행이 아니면 여기서 실패한다.
ALTER TABLE favorite
    ADD CONSTRAINT uk_favorite_account_ref UNIQUE (account_id, ref_type, ref_id);

-- 대상 기준 조회 전용. UNIQUE 인덱스는 account_id가 선행 컬럼이라
-- 대상별 카운트·통계·CASCADE 삭제(ref_type + ref_id)에 쓰이지 못한다.
CREATE INDEX idx_favorite_ref ON favorite (ref_type, ref_id);

-- 내 찜 목록(타입별 + 등록 최신순 + PK tie-break) 전용.
-- UNIQUE 인덱스는 세 번째 컬럼이 ref_id라 created_at 정렬에 쓰이지 못한다.
CREATE INDEX idx_favorite_account_type_created
    ON favorite (account_id, ref_type, created_at, favorite_id);

-- 중고 공개 목록 기본 조회(지역 + 삭제·숨김 제외 + 최신순) 전용.
-- 기존 idx_used_product_region_status는 status가 선행이라 상태 필터가 없으면 created_at까지 닿지 못한다.
-- 운영 데이터로 EXPLAIN 확인 후 적용할 것.
CREATE INDEX idx_used_product_public_list
    ON used_product (region_id, deleted_at, is_hidden, created_at);

-- 5) 2에서 중복을 지웠다면 카운트가 실제 행 수와 어긋난다. 배포 후 아래 API로 재계산한다.
--      POST /admin/favorites/recalculate          (상점 + 중고 게시글 전체)
--      POST /admin/favorites/recalculate?refType=USED_PRODUCT
