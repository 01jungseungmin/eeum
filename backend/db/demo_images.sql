-- =============================================
-- 공모전 시연 영상용 이미지 패치
--   via.placeholder.com 서비스 종료로 기존 더미 이미지가 전부 깨짐.
--   실제 로딩되는 Unsplash CDN 직링크로 교체 + 누락된 store_image 채움.
--   재시딩 없이 실행 중인 DB에 바로 적용 가능 (멱등).
--
--   주의: 한글 깨짐이 있다면 fix_mojibake.sql 을 먼저 실행할 것.
-- =============================================

SET @W = '?w=800&h=600&fit=crop';
SET @S = '?w=1200&h=800&fit=crop';
SET @U = 'https://images.unsplash.com/';

-- ---------------------------------------------
-- 1) 반찬가게 / 카페 상품 (product_image 1~11)
-- ---------------------------------------------
UPDATE product_image SET image_url = CONCAT(@U,'photo-1580651315530-69c8e0026377',@W) WHERE product_image_id = 1;  -- 김치찌개
UPDATE product_image SET image_url = CONCAT(@U,'photo-1635363638580-c2809d049eee',@W) WHERE product_image_id = 2;  -- 된장찌개
UPDATE product_image SET image_url = CONCAT(@U,'photo-1590301157890-4810ed352733',@W) WHERE product_image_id = 3;  -- 깍두기
UPDATE product_image SET image_url = CONCAT(@U,'photo-1553163147-622ab57be1c7',@W)    WHERE product_image_id = 4;  -- 깍두기-2
UPDATE product_image SET image_url = CONCAT(@U,'photo-1548943487-a2e4e43b4853',@W)    WHERE product_image_id = 5;  -- 배추김치
UPDATE product_image SET image_url = CONCAT(@U,'photo-1546069901-ba9599a7e63c',@W)    WHERE product_image_id = 6;  -- 도시락 예약
UPDATE product_image SET image_url = CONCAT(@U,'photo-1447933601403-0c6688de566e',@W) WHERE product_image_id = 7;  -- 아메리카노
UPDATE product_image SET image_url = CONCAT(@U,'photo-1509042239860-f550ce710b93',@W) WHERE product_image_id = 8;  -- 카페라떼
UPDATE product_image SET image_url = CONCAT(@U,'photo-1551024506-0bccd828d307',@W)    WHERE product_image_id = 9;  -- 초코스무디
UPDATE product_image SET image_url = CONCAT(@U,'photo-1484723091739-30a097e8f929',@W) WHERE product_image_id = 10; -- 크로플
UPDATE product_image SET image_url = CONCAT(@U,'photo-1488477181946-6428a0291777',@W) WHERE product_image_id = 11; -- 티라미수

-- ---------------------------------------------
-- 2) 계양 상점 20~35 의 상품 이미지 (업종별 2장씩)
-- ---------------------------------------------
UPDATE product_image pi JOIN product p ON pi.product_id = p.product_id
SET pi.image_url = CONCAT(@U, CASE p.store_id
    WHEN 20 THEN IF(pi.display_order=1,'photo-1498654896293-37aacf113fd9','photo-1553163147-622ab57be1c7')  -- 맛집
    WHEN 21 THEN IF(pi.display_order=1,'photo-1509042239860-f550ce710b93','photo-1495474472287-4d71bcdd2085')  -- 카페
    WHEN 22 THEN IF(pi.display_order=1,'photo-1509440159596-0249088772ff','photo-1555507036-ab1f4038808a')  -- 베이커리
    WHEN 23 THEN IF(pi.display_order=1,'photo-1590301157890-4810ed352733','photo-1546069901-ba9599a7e63c')  -- 반찬/도시락
    WHEN 24 THEN IF(pi.display_order=1,'photo-1607623814075-e51df1bdc82f','photo-1498654200943-1088dd4438ae')  -- 정육/수산
    WHEN 25 THEN IF(pi.display_order=1,'photo-1542838132-92c53300491e','photo-1488459716781-31db52582fe9')  -- 농산물/청과
    WHEN 26 THEN IF(pi.display_order=1,'photo-1578916171728-46686eac8d58','photo-1604719312566-8912e9227c6a')  -- 편의점/마트
    WHEN 27 THEN IF(pi.display_order=1,'photo-1563241527-3004b7be0ffd','photo-1487070183336-b863922373d4')  -- 꽃/식물
    WHEN 28 THEN IF(pi.display_order=1,'photo-1560066984-138dadb4c035','photo-1522337360788-8b13dee7a37e')  -- 뷰티/미용
    WHEN 29 THEN IF(pi.display_order=1,'photo-1576602976047-174e57a47881','photo-1584308666744-24d5c474f2ae')  -- 건강/의료
    WHEN 30 THEN IF(pi.display_order=1,'photo-1545173168-9f1947eebb7f','photo-1582735689369-4fe89db7114c')  -- 생활서비스
    WHEN 31 THEN IF(pi.display_order=1,'photo-1497633762265-9d179a990aa6','photo-1503676260728-1c00da094a0b')  -- 교육/학원
    WHEN 32 THEN IF(pi.display_order=1,'photo-1450778869180-41d0601e046e','photo-1583337130417-3346a1be7dee')  -- 반려동물
    WHEN 33 THEN IF(pi.display_order=1,'photo-1507924538820-ede94a04019d','photo-1499364615650-ec38552f4f34')  -- 문화/여가
    WHEN 34 THEN IF(pi.display_order=1,'photo-1441986300917-64674bd600d8','photo-1445205170230-053b83016050')  -- 패션/잡화
    WHEN 35 THEN IF(pi.display_order=1,'photo-1513475382585-d06e58bcb0e0','photo-1441986300917-64674bd600d8')  -- 기타
    ELSE NULL END, @W)
WHERE p.store_id BETWEEN 20 AND 35;

-- ---------------------------------------------
-- 3) 상점 대표 이미지 (기존 더미에 아예 없던 데이터)
-- ---------------------------------------------
INSERT INTO store_image (store_id, image_url, display_order, is_thumbnail, created_at, modified_at)
SELECT s.store_id, CONCAT(@U, t.pid, @S), 1, TRUE, NOW(), NOW()
FROM store s JOIN (
    SELECT  1 AS sid, 'photo-1498654896293-37aacf113fd9' AS pid UNION ALL
    SELECT  2, 'photo-1521017432531-fbd92d768814' UNION ALL
    SELECT  3, 'photo-1546069901-ba9599a7e63c' UNION ALL
    SELECT 20, 'photo-1517248135467-4c7edcad34c4' UNION ALL
    SELECT 21, 'photo-1521017432531-fbd92d768814' UNION ALL
    SELECT 22, 'photo-1509440159596-0249088772ff' UNION ALL
    SELECT 23, 'photo-1590301157890-4810ed352733' UNION ALL
    SELECT 24, 'photo-1607623814075-e51df1bdc82f' UNION ALL
    SELECT 25, 'photo-1542838132-92c53300491e' UNION ALL
    SELECT 26, 'photo-1578916171728-46686eac8d58' UNION ALL
    SELECT 27, 'photo-1563241527-3004b7be0ffd' UNION ALL
    SELECT 28, 'photo-1560066984-138dadb4c035' UNION ALL
    SELECT 29, 'photo-1576602976047-174e57a47881' UNION ALL
    SELECT 30, 'photo-1545173168-9f1947eebb7f' UNION ALL
    SELECT 31, 'photo-1497633762265-9d179a990aa6' UNION ALL
    SELECT 32, 'photo-1450778869180-41d0601e046e' UNION ALL
    SELECT 33, 'photo-1507924538820-ede94a04019d' UNION ALL
    SELECT 34, 'photo-1441986300917-64674bd600d8' UNION ALL
    SELECT 35, 'photo-1513475382585-d06e58bcb0e0'
) t ON t.sid = s.store_id
WHERE NOT EXISTS (SELECT 1 FROM store_image si WHERE si.store_id = s.store_id);
