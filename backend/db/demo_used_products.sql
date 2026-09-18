-- =============================================
-- 중고거래 데모 게시글
--   dummy.sql에는 used_product 행이 한 건도 없어 중고거래 탭이 빈 화면으로 뜬다.
--   공모전 웹 데모에서 핵심 기능(목록·카테고리 필터·정렬·거래 희망 장소 지도)을
--   바로 보여주려면 최소한의 실제 데이터가 필요하다.
--
--   반드시 utf8mb4 커넥션으로 적재할 것 (dummy.sql 헤더 참고).
--     docker exec -i eeum-mysql mysql -uroot -proot \
--       --default-character-set=utf8mb4 eeum < backend/db/demo_used_products.sql
--
--   멱등: 이미 같은 id가 있으면 건너뛴다. 재실행해도 중복되지 않는다.
--
--   전제 — dummy.sql이 먼저 적재돼 있어야 한다.
--     account  1~3 (user1~3@test.com)
--     category 13~18 (CategoryType.USED)
--     region   4~7 (서울 종로구 청운동/신교동/궁정동/효자동)
-- =============================================

SET @U = 'https://images.unsplash.com/';
SET @W = '?w=800&h=600&fit=crop';

INSERT INTO used_product (
    used_product_id, account_id, category_id, region_id, title, content,
    price_type, price, trade_location_name, trade_latitude, trade_longitude, trade_place_id,
    status, buyer_account_id, is_hidden, deleted_at, view_count, favorite_count,
    created_at, modified_at
)
SELECT * FROM (
    SELECT 1 AS a, 1 AS b, 13 AS c, 4 AS d, '아이패드 에어 5세대 64GB 스페이스그레이' AS e,
           '작년에 구매해서 인강용으로만 썼습니다. 액정 기스 없고 배터리 성능 94%입니다.\n애플펜슬 2세대는 포함되지 않습니다. 청운동 근처에서 직거래 희망합니다.' AS f,
           'FIXED' AS g, 480000.00 AS h, '경복궁역 3번 출구' AS i, 37.5759 AS j, 126.9735 AS k, NULL AS l,
           'SELLING' AS m, NULL AS n, FALSE AS o, NULL AS p, 128 AS q, 9 AS r,
           DATE_SUB(NOW(), INTERVAL 2 DAY) AS s, DATE_SUB(NOW(), INTERVAL 2 DAY) AS t
    UNION ALL SELECT 2, 2, 15, 4, '원목 4인용 식탁 (의자 2개 포함)',
           '이사 가면서 내놓습니다. 상판에 생활기스 조금 있지만 튼튼합니다.\n직접 가져가실 분만 연락 주세요. 엘리베이터 있습니다.',
           'FIXED', 85000.00, '청운효자동 주민센터 앞', 37.5845, 126.9679, NULL,
           'RESERVED', 3, FALSE, NULL, 76, 4,
           DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)
    UNION ALL SELECT 3, 3, 14, 5, '나이키 에어포스1 흰색 260mm',
           '두 번 신고 사이즈가 안 맞아 판매합니다. 박스, 여분 끈 다 있습니다.',
           'NEGOTIABLE', NULL, '신교동 버스정류장', 37.5852, 126.9668, NULL,
           'SELLING', NULL, FALSE, NULL, 54, 6,
           DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)
    UNION ALL SELECT 4, 1, 16, 6, '토익 RC/LC 기본서 세트 나눔합니다',
           '시험 끝나서 필요하신 분께 무료로 드립니다. 필기 조금 있어요.\n궁정동 카페 앞에서 만나서 드리겠습니다.',
           'FREE', 0.00, '궁정동 카페거리 입구', 37.5867, 126.9721, NULL,
           'SELLING', NULL, FALSE, NULL, 203, 21,
           DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)
    UNION ALL SELECT 5, 2, 17, 4, '다이슨 에어랩 컴플리트 (정품, 리퍼)',
           '공식 리퍼 제품이고 구성품 전부 있습니다. 실사용 3개월입니다.',
           'FIXED', 390000.00, '청운동 공영주차장', 37.5892, 126.9693, NULL,
           'SOLD', 1, FALSE, NULL, 341, 33,
           DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)
    UNION ALL SELECT 6, 3, 18, 7, '캠핑 의자 2개 + 접이식 테이블',
           '작년에 두 번 쓰고 창고에 있었습니다. 상태 좋습니다.\n가격 제안 주시면 조율 가능합니다.',
           'NEGOTIABLE', NULL, '효자동 삼거리', 37.5836, 126.9716, NULL,
           'SELLING', NULL, FALSE, NULL, 47, 3,
           DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM used_product WHERE used_product_id BETWEEN 1 AND 6);

INSERT INTO used_product_image (
    used_product_id, image_url, display_order, is_thumbnail, created_at, modified_at
)
SELECT t.pid, CONCAT(@U, t.photo, @W), t.ord, t.thumb, NOW(), NOW()
FROM (
    SELECT 1 AS pid, 'photo-1544244015-0df4b3ffc6b0' AS photo, 1 AS ord, TRUE  AS thumb
    UNION ALL SELECT 1, 'photo-1561154464-82e9adf32764',   2, FALSE
    UNION ALL SELECT 2, 'photo-1617806118233-18e1de247200', 1, TRUE
    UNION ALL SELECT 2, 'photo-1615873968403-89e068629265', 2, FALSE
    UNION ALL SELECT 3, 'photo-1549298916-b41d501d3772',   1, TRUE
    UNION ALL SELECT 4, 'photo-1544947950-fa07a98d237f',   1, TRUE
    UNION ALL SELECT 5, 'photo-1522338140262-f46f5913618a', 1, TRUE
    UNION ALL SELECT 6, 'photo-1504280390367-361c6d9f38f4', 1, TRUE
) t
WHERE EXISTS (SELECT 1 FROM used_product up WHERE up.used_product_id = t.pid)
  AND NOT EXISTS (
      SELECT 1 FROM used_product_image upi
      WHERE upi.used_product_id = t.pid AND upi.display_order = t.ord
  );
