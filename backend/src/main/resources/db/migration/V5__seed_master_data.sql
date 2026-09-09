-- =========================================================
-- 공통 마스터 데이터 seed (category)
--
-- 기존 클라이언트가 카테고리 ID를 상수로 들고 있어 ID를 명시적으로 고정한다.
--   STORE      1~7    (frontend-app/constants/shopDummyData.ts   SHOP_CATEGORIES)
--   COMMUNITY  8~12   (frontend-app/app/community/write.tsx      CATEGORY_MAP)
--   USED       13~18  (frontend-app/constants/usedCategories.ts  USED_CATEGORIES)
-- 이 18개 행의 ID/이름은 절대 바꾸지 않는다. 확장분은 100번대부터 채운다.
--
-- Flyway 마이그레이션은 성공 시 한 번만 실행된다. INSERT IGNORE를 쓰지 않아
-- 고정 ID가 다른 데이터와 충돌하면 배포를 중단한다. 충돌을 조용히 넘기면
-- 프론트가 기대하는 ID와 실제 카테고리의 의미가 달라질 수 있다.
--
-- parent_scope는 parent_id의 NULL 안전 사본이다(루트=0, 자식=parent_id).
-- =========================================================

-- =========================================================
-- 1. STORE — 가게 업종 (depth 1만)
--
-- StoreRepositoryImpl.categoryEq()가 categoryId 완전 일치로만 필터해
-- 상위 카테고리 검색 시 하위 가게가 잡히지 않는다. 그래서 STORE는
-- 중분류를 만들지 않는다. 중분류가 필요해지면 하위 전개 로직을
-- 먼저 넣고(UsedProductService.resolveCategoryIds 참고) 추가한다.
-- =========================================================
INSERT INTO category
    (category_id, type, parent_id, parent_scope, name, display_order, depth, is_active, version, created_at, modified_at)
VALUES
    (  1, 'STORE', NULL, 0, '음식점',       1, 1, 1, 0, NOW(6), NOW(6)),
    (  2, 'STORE', NULL, 0, '카페/디저트',   2, 1, 1, 0, NOW(6), NOW(6)),
    (  5, 'STORE', NULL, 0, '베이커리',      3, 1, 1, 0, NOW(6), NOW(6)),
    (  3, 'STORE', NULL, 0, '반찬/도시락',   4, 1, 1, 0, NOW(6), NOW(6)),
    (  4, 'STORE', NULL, 0, '정육/수산',     5, 1, 1, 0, NOW(6), NOW(6)),
    (100, 'STORE', NULL, 0, '농산물/청과',   6, 1, 1, 0, NOW(6), NOW(6)),
    (  6, 'STORE', NULL, 0, '편의점/마트',   7, 1, 1, 0, NOW(6), NOW(6)),
    (101, 'STORE', NULL, 0, '꽃/식물',       8, 1, 1, 0, NOW(6), NOW(6)),
    (102, 'STORE', NULL, 0, '뷰티/미용',     9, 1, 1, 0, NOW(6), NOW(6)),
    (103, 'STORE', NULL, 0, '건강/의료',    10, 1, 1, 0, NOW(6), NOW(6)),
    (104, 'STORE', NULL, 0, '생활서비스',   11, 1, 1, 0, NOW(6), NOW(6)),
    (105, 'STORE', NULL, 0, '교육/학원',    12, 1, 1, 0, NOW(6), NOW(6)),
    (106, 'STORE', NULL, 0, '반려동물',     13, 1, 1, 0, NOW(6), NOW(6)),
    (107, 'STORE', NULL, 0, '문화/여가',    14, 1, 1, 0, NOW(6), NOW(6)),
    (108, 'STORE', NULL, 0, '패션/잡화',    15, 1, 1, 0, NOW(6), NOW(6)),
    (  7, 'STORE', NULL, 0, '기타',         99, 1, 1, 0, NOW(6), NOW(6));

-- =========================================================
-- 2. COMMUNITY — 동네생활 게시판 (depth 1만)
-- 게시판 성격상 중분류를 쓰지 않는다.
-- =========================================================
INSERT INTO category
    (category_id, type, parent_id, parent_scope, name, display_order, depth, is_active, version, created_at, modified_at)
VALUES
    (  8, 'COMMUNITY', NULL, 0, '자유게시판',  1, 1, 1, 0, NOW(6), NOW(6)),
    (  9, 'COMMUNITY', NULL, 0, '동네소식',    2, 1, 1, 0, NOW(6), NOW(6)),
    (240, 'COMMUNITY', NULL, 0, '맛집',        3, 1, 1, 0, NOW(6), NOW(6)),
    (241, 'COMMUNITY', NULL, 0, '동네행사',    4, 1, 1, 0, NOW(6), NOW(6)),
    ( 10, 'COMMUNITY', NULL, 0, '분실물',      5, 1, 1, 0, NOW(6), NOW(6)),
    ( 11, 'COMMUNITY', NULL, 0, '도움요청',    6, 1, 1, 0, NOW(6), NOW(6)),
    ( 12, 'COMMUNITY', NULL, 0, '공동배달',    7, 1, 1, 0, NOW(6), NOW(6)),
    (242, 'COMMUNITY', NULL, 0, '취미/모임',   8, 1, 1, 0, NOW(6), NOW(6)),
    (243, 'COMMUNITY', NULL, 0, '운동',        9, 1, 1, 0, NOW(6), NOW(6)),
    (244, 'COMMUNITY', NULL, 0, '반려동물',   10, 1, 1, 0, NOW(6), NOW(6)),
    (245, 'COMMUNITY', NULL, 0, '생활/편의',  11, 1, 1, 0, NOW(6), NOW(6)),
    (246, 'COMMUNITY', NULL, 0, '병원/약국',  12, 1, 1, 0, NOW(6), NOW(6)),
    (247, 'COMMUNITY', NULL, 0, '이사/시공',  13, 1, 1, 0, NOW(6), NOW(6)),
    (248, 'COMMUNITY', NULL, 0, '주거/부동산',14, 1, 1, 0, NOW(6), NOW(6)),
    (249, 'COMMUNITY', NULL, 0, '교육/육아',  15, 1, 1, 0, NOW(6), NOW(6)),
    (250, 'COMMUNITY', NULL, 0, '고민/사연',  16, 1, 1, 0, NOW(6), NOW(6)),
    (251, 'COMMUNITY', NULL, 0, '동네친구',   17, 1, 1, 0, NOW(6), NOW(6));

-- =========================================================
-- 3. USED — 중고거래 (depth 1 + depth 2)
--
-- UsedProductService.resolveCategoryIds()가 선택한 카테고리의 하위를
-- 전부 펼쳐서 검색하므로 중분류를 안전하게 쓸 수 있다.
-- =========================================================

-- 3-1. USED 대분류
INSERT INTO category
    (category_id, type, parent_id, parent_scope, name, display_order, depth, is_active, version, created_at, modified_at)
VALUES
    ( 13, 'USED', NULL, 0, '디지털/가전',    1, 1, 1, 0, NOW(6), NOW(6)),
    ( 14, 'USED', NULL, 0, '의류/잡화',      2, 1, 1, 0, NOW(6), NOW(6)),
    ( 15, 'USED', NULL, 0, '가구/인테리어',  3, 1, 1, 0, NOW(6), NOW(6)),
    (120, 'USED', NULL, 0, '생활/주방',      4, 1, 1, 0, NOW(6), NOW(6)),
    (121, 'USED', NULL, 0, '유아동',         5, 1, 1, 0, NOW(6), NOW(6)),
    (122, 'USED', NULL, 0, '뷰티/미용',      6, 1, 1, 0, NOW(6), NOW(6)),
    ( 17, 'USED', NULL, 0, '스포츠/레저',    7, 1, 1, 0, NOW(6), NOW(6)),
    (123, 'USED', NULL, 0, '취미/게임',      8, 1, 1, 0, NOW(6), NOW(6)),
    ( 16, 'USED', NULL, 0, '도서/음반',      9, 1, 1, 0, NOW(6), NOW(6)),
    (124, 'USED', NULL, 0, '티켓/교환권',   10, 1, 1, 0, NOW(6), NOW(6)),
    (125, 'USED', NULL, 0, '식품',          11, 1, 1, 0, NOW(6), NOW(6)),
    (126, 'USED', NULL, 0, '반려동물용품',  12, 1, 1, 0, NOW(6), NOW(6)),
    (127, 'USED', NULL, 0, '식물',          13, 1, 1, 0, NOW(6), NOW(6)),
    ( 18, 'USED', NULL, 0, '기타',          99, 1, 1, 0, NOW(6), NOW(6));

-- 3-2. USED 중분류
INSERT INTO category
    (category_id, type, parent_id, parent_scope, name, display_order, depth, is_active, version, created_at, modified_at)
VALUES
    -- 디지털/가전 (13)
    (130, 'USED',  13,  13, '휴대폰/태블릿',     1, 2, 1, 0, NOW(6), NOW(6)),
    (131, 'USED',  13,  13, '노트북/PC',         2, 2, 1, 0, NOW(6), NOW(6)),
    (132, 'USED',  13,  13, '카메라',            3, 2, 1, 0, NOW(6), NOW(6)),
    (133, 'USED',  13,  13, '게임기',            4, 2, 1, 0, NOW(6), NOW(6)),
    (134, 'USED',  13,  13, '음향기기',          5, 2, 1, 0, NOW(6), NOW(6)),
    (135, 'USED',  13,  13, 'TV/영상가전',       6, 2, 1, 0, NOW(6), NOW(6)),
    (136, 'USED',  13,  13, '주방가전',          7, 2, 1, 0, NOW(6), NOW(6)),
    (137, 'USED',  13,  13, '생활가전',          8, 2, 1, 0, NOW(6), NOW(6)),
    (138, 'USED',  13,  13, '계절가전',          9, 2, 1, 0, NOW(6), NOW(6)),

    -- 의류/잡화 (14)
    (140, 'USED',  14,  14, '여성의류',          1, 2, 1, 0, NOW(6), NOW(6)),
    (141, 'USED',  14,  14, '남성의류',          2, 2, 1, 0, NOW(6), NOW(6)),
    (142, 'USED',  14,  14, '신발',              3, 2, 1, 0, NOW(6), NOW(6)),
    (143, 'USED',  14,  14, '가방/지갑',         4, 2, 1, 0, NOW(6), NOW(6)),
    (144, 'USED',  14,  14, '시계/주얼리',       5, 2, 1, 0, NOW(6), NOW(6)),
    (145, 'USED',  14,  14, '패션 액세서리',     6, 2, 1, 0, NOW(6), NOW(6)),

    -- 가구/인테리어 (15)
    (150, 'USED',  15,  15, '침실가구',          1, 2, 1, 0, NOW(6), NOW(6)),
    (151, 'USED',  15,  15, '거실가구',          2, 2, 1, 0, NOW(6), NOW(6)),
    (152, 'USED',  15,  15, '주방/식탁가구',     3, 2, 1, 0, NOW(6), NOW(6)),
    (153, 'USED',  15,  15, '수납/서랍',         4, 2, 1, 0, NOW(6), NOW(6)),
    (154, 'USED',  15,  15, '조명/인테리어소품', 5, 2, 1, 0, NOW(6), NOW(6)),
    (155, 'USED',  15,  15, '커튼/침구',         6, 2, 1, 0, NOW(6), NOW(6)),

    -- 생활/주방 (120)
    (160, 'USED', 120, 120, '주방용품',          1, 2, 1, 0, NOW(6), NOW(6)),
    (161, 'USED', 120, 120, '생활용품',          2, 2, 1, 0, NOW(6), NOW(6)),
    (162, 'USED', 120, 120, '욕실용품',          3, 2, 1, 0, NOW(6), NOW(6)),
    (163, 'USED', 120, 120, '공구/자재',         4, 2, 1, 0, NOW(6), NOW(6)),

    -- 유아동 (121)
    (170, 'USED', 121, 121, '유아동 의류',       1, 2, 1, 0, NOW(6), NOW(6)),
    (171, 'USED', 121, 121, '유아동 신발',       2, 2, 1, 0, NOW(6), NOW(6)),
    (172, 'USED', 121, 121, '유모차/카시트',     3, 2, 1, 0, NOW(6), NOW(6)),
    (173, 'USED', 121, 121, '장난감/교구',       4, 2, 1, 0, NOW(6), NOW(6)),
    (174, 'USED', 121, 121, '수유/이유용품',     5, 2, 1, 0, NOW(6), NOW(6)),
    (175, 'USED', 121, 121, '유아도서',          6, 2, 1, 0, NOW(6), NOW(6)),

    -- 뷰티/미용 (122)
    (180, 'USED', 122, 122, '화장품',            1, 2, 1, 0, NOW(6), NOW(6)),
    (181, 'USED', 122, 122, '헤어/바디',         2, 2, 1, 0, NOW(6), NOW(6)),
    (182, 'USED', 122, 122, '향수',              3, 2, 1, 0, NOW(6), NOW(6)),
    (183, 'USED', 122, 122, '미용기기',          4, 2, 1, 0, NOW(6), NOW(6)),

    -- 스포츠/레저 (17)
    (190, 'USED',  17,  17, '자전거',            1, 2, 1, 0, NOW(6), NOW(6)),
    (191, 'USED',  17,  17, '헬스/요가',         2, 2, 1, 0, NOW(6), NOW(6)),
    (192, 'USED',  17,  17, '캠핑/등산',         3, 2, 1, 0, NOW(6), NOW(6)),
    (193, 'USED',  17,  17, '골프',              4, 2, 1, 0, NOW(6), NOW(6)),
    (194, 'USED',  17,  17, '낚시',              5, 2, 1, 0, NOW(6), NOW(6)),
    (195, 'USED',  17,  17, '구기/라켓',         6, 2, 1, 0, NOW(6), NOW(6)),

    -- 취미/게임 (123)
    (200, 'USED', 123, 123, '게임 타이틀',       1, 2, 1, 0, NOW(6), NOW(6)),
    (202, 'USED', 123, 123, '악기',              2, 2, 1, 0, NOW(6), NOW(6)),
    (203, 'USED', 123, 123, '피규어/굿즈',       3, 2, 1, 0, NOW(6), NOW(6)),
    (204, 'USED', 123, 123, '보드게임/퍼즐',     4, 2, 1, 0, NOW(6), NOW(6)),
    (205, 'USED', 123, 123, '수집품',            5, 2, 1, 0, NOW(6), NOW(6)),

    -- 도서/음반 (16)
    (210, 'USED',  16,  16, '소설/에세이',       1, 2, 1, 0, NOW(6), NOW(6)),
    (211, 'USED',  16,  16, '자기계발/경제',     2, 2, 1, 0, NOW(6), NOW(6)),
    (212, 'USED',  16,  16, '학습/참고서',       3, 2, 1, 0, NOW(6), NOW(6)),
    (213, 'USED',  16,  16, '만화책',            4, 2, 1, 0, NOW(6), NOW(6)),
    (214, 'USED',  16,  16, '잡지',              5, 2, 1, 0, NOW(6), NOW(6)),
    (201, 'USED',  16,  16, '음반/LP',           6, 2, 1, 0, NOW(6), NOW(6)),

    -- 식품 (125)
    (220, 'USED', 125, 125, '가공식품',          1, 2, 1, 0, NOW(6), NOW(6)),
    (221, 'USED', 125, 125, '건강기능식품',      2, 2, 1, 0, NOW(6), NOW(6)),
    (222, 'USED', 125, 125, '농수산물',          3, 2, 1, 0, NOW(6), NOW(6)),

    -- 반려동물용품 (126)
    (230, 'USED', 126, 126, '사료/간식',         1, 2, 1, 0, NOW(6), NOW(6)),
    (231, 'USED', 126, 126, '반려동물 용품',     2, 2, 1, 0, NOW(6), NOW(6));

-- =========================================================
-- 검증 (수동 확인용 — 마이그레이션 후 직접 실행)
--
-- SELECT type, depth, COUNT(*) FROM category GROUP BY type, depth ORDER BY type, depth;
--   COMMUNITY / 1 / 17
--   STORE     / 1 / 16
--   USED      / 1 / 14
--   USED      / 2 / 57
--
-- 클라이언트가 상수로 들고 있는 ID가 그대로인지 확인:
-- SELECT category_id, type, name FROM category WHERE category_id BETWEEN 1 AND 18 ORDER BY category_id;
--
-- 고아 카테고리(부모 없는 depth 2)가 없는지 확인:
-- SELECT c.category_id, c.name FROM category c
--   LEFT JOIN category p ON p.category_id = c.parent_id
--  WHERE c.parent_id IS NOT NULL AND p.category_id IS NULL;
-- =========================================================
