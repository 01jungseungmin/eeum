-- =============================================
-- 이음(Eeum) 더미 데이터
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. Region 더미 데이터
-- =============================================

INSERT INTO region (region_id, region_code, si_do, gun_gu, dong, radius) VALUES
(4, '1111010100', '서울특별시', '종로구', '청운동', 1000),
(5, '1111010200', '서울특별시', '종로구', '신교동', 1000),
(6, '1111010300', '서울특별시', '종로구', '궁정동', 1000),
(7, '1111010400', '서울특별시', '종로구', '효자동', 1000);

-- =============================================
-- 2. Location 더미 데이터
-- =============================================

INSERT INTO location (location_id, region_id, latitude, longitude) VALUES
(1001, 4, 37.5891974378627, 126.969329763593),
(1002, 5, 37.5844902776102, 126.967923271581),
(1003, 6, 37.5846825915783, 126.973144784748),
(1004, 7, 37.5826496685116, 126.971931855344);

-- =============================================
-- 3. Category 더미 데이터
-- =============================================

-- STORE 업종 카테고리
INSERT INTO category (category_id, type, parent_id, name, display_order, depth, is_active, created_at, modified_at, deleted_at) VALUES
(1, 'STORE', NULL, '음식점',       1, 1, TRUE, NOW(), NOW(), NULL),
(2, 'STORE', NULL, '카페/디저트',  2, 1, TRUE, NOW(), NOW(), NULL),
(3, 'STORE', NULL, '반찬/도시락',  3, 1, TRUE, NOW(), NOW(), NULL),
(4, 'STORE', NULL, '정육/수산',    4, 1, TRUE, NOW(), NOW(), NULL),
(5, 'STORE', NULL, '베이커리',     5, 1, TRUE, NOW(), NOW(), NULL),
(6, 'STORE', NULL, '편의점/마트',  6, 1, TRUE, NOW(), NOW(), NULL),
(7, 'STORE', NULL, '기타',         7, 1, TRUE, NOW(), NOW(), NULL);

-- COMMUNITY 카테고리
INSERT INTO category (category_id, type, parent_id, name, display_order, depth, is_active, created_at, modified_at, deleted_at) VALUES
(8,  'COMMUNITY', NULL, '자유게시판', 1, 1, TRUE, NOW(), NOW(), NULL),
(9,  'COMMUNITY', NULL, '동네소식',   2, 1, TRUE, NOW(), NOW(), NULL),
(10, 'COMMUNITY', NULL, '분실물',     3, 1, TRUE, NOW(), NOW(), NULL),
(11, 'COMMUNITY', NULL, '도움요청',   4, 1, TRUE, NOW(), NOW(), NULL),
(12, 'COMMUNITY', NULL, '공동배달',   5, 1, TRUE, NOW(), NOW(), NULL);

-- USED 카테고리
INSERT INTO category (category_id, type, parent_id, name, display_order, depth, is_active, created_at, modified_at, deleted_at) VALUES
(13, 'USED', NULL, '디지털/가전',   1, 1, TRUE, NOW(), NOW(), NULL),
(14, 'USED', NULL, '의류/잡화',     2, 1, TRUE, NOW(), NOW(), NULL),
(15, 'USED', NULL, '가구/인테리어', 3, 1, TRUE, NOW(), NOW(), NULL),
(16, 'USED', NULL, '도서/음반',     4, 1, TRUE, NOW(), NOW(), NULL),
(17, 'USED', NULL, '스포츠/레저',   5, 1, TRUE, NOW(), NOW(), NULL),
(18, 'USED', NULL, '기타',          6, 1, TRUE, NOW(), NOW(), NULL);

-- =============================================
-- 4. Account 더미 데이터
-- BCrypt 해시값: password1! → $2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2
-- =============================================

INSERT INTO account (account_id, primary_region_id, email, password, name, phone, provider, provider_id, profile_image_url, nickname, role, status, email_verified, fcm_token, created_at, modified_at, deleted_at) VALUES
-- 일반 사용자 (ROLE_USER)
(1, 4, 'user1@test.com',  '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '홍길동', '010-1111-1111', 'LOCAL', NULL, '/default-profile.png', '동네주민1', 'ROLE_USER',  'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
(2, 5, 'user2@test.com',  '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '김철수', '010-2222-2222', 'LOCAL', NULL, '/default-profile.png', '동네주민2', 'ROLE_USER',  'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
(3, 6, 'user3@test.com',  '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '이영희', '010-3333-3333', 'LOCAL', NULL, '/default-profile.png', '동네주민3', 'ROLE_USER',  'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
-- 사장 (ROLE_OWNER) - 승인됨
(4, 4, 'owner1@test.com', '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '박사장', '010-4444-4444', 'LOCAL', NULL, '/default-profile.png', '사장님1',  'ROLE_OWNER', 'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
-- 사장 신청 중 (ROLE_USER - 승인 대기)
(5, 5, 'owner2@test.com', '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '최사장', '010-5555-5555', 'LOCAL', NULL, '/default-profile.png', '사장님2',  'ROLE_USER',  'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
-- 사장 신청 거절 (ROLE_USER - 거절됨)
(6, 6, 'owner3@test.com', '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '강사장', '010-6666-6666', 'LOCAL', NULL, '/default-profile.png', '사장님3',  'ROLE_USER',  'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL),
-- 관리자 (ROLE_ADMIN)
(7, NULL, 'admin@test.com',  '$2a$10$Z2AF88QoikQGMSUnP/iSYu.j8ZqegwlxO4mRoPNQ8DWyTsgKpcWm2', '관리자', '010-7777-7777', 'LOCAL', NULL, '/default-profile.png', '관리자',   'ROLE_ADMIN', 'ACTIVE', TRUE, NULL, NOW(), NOW(), NULL);

-- =============================================
-- 5. AccountRegion 더미 데이터
-- =============================================

INSERT INTO account_region (account_region_id, account_id, region_id, verified, verified_at, created_at, modified_at) VALUES
(1, 1, 4, TRUE,  NOW(), NOW(), NOW()),
(2, 2, 5, TRUE,  NOW(), NOW(), NOW()),
(3, 3, 6, TRUE,  NOW(), NOW(), NOW()),
(4, 4, 4, TRUE,  NOW(), NOW(), NOW()),
(5, 5, 5, TRUE,  NOW(), NOW(), NOW()),
(6, 6, 6, TRUE,  NOW(), NOW(), NOW());

-- =============================================
-- 6. OwnerInfo 더미 데이터
-- =============================================

INSERT INTO owner_info (owner_info_id, account_id, business_number, opening_date, approval_status, rejection_reason, review_requested_at, created_at, modified_at) VALUES
-- 승인된 사장
(1, 4, '1234567890', '2020-01-01', 'APPROVED', NULL,              NOW(), NOW(), NOW()),
-- 대기 중인 사장 (심사 요청 완료)
(2, 5, '2345678901', '2021-05-15', 'PENDING',  NULL,              NOW(), NOW(), NOW()),
-- 거절된 사장
(3, 6, '3456789012', '2019-03-20', 'REJECTED', '서류 미비 및 사업자 정보 불일치', NULL, NOW(), NOW());

-- =============================================
-- 7. Store 더미 데이터
-- =============================================

INSERT INTO store (store_id, account_id, region_id, category_id, latitude, longitude, name, address, phone, description, business_hours, rating, favorite_count, review_count, status, version, created_at, modified_at) VALUES
-- 승인된 사장 상점 (OPEN)
(1, 4, 4, 1, 37.5891974378627, 126.969329763593, '맛있는 반찬가게', '서울특별시 종로구 청운동 123-4', '02-1234-5678', '매일 신선한 반찬을 만들어요!', '월~토 09:00~19:00', 4.5, 10, 5, 'OPEN', 0, NOW(), NOW()),
-- 대기 중인 사장 상점 (TEMP_CLOSED)
(2, 5, 5, 2, 37.5844902776102, 126.967923271581, '청운 카페',      '서울특별시 종로구 신교동 456-7', '02-2345-6789', '편안한 카페입니다.',           '월~일 10:00~21:00', 0.0, 0,  0, 'TEMP_CLOSED', 0, NOW(), NOW()),
-- 거절된 사장 상점 (TEMP_CLOSED)
(3, 6, 6, 3, 37.5846825915783, 126.973144784748, '궁정 도시락',    '서울특별시 종로구 궁정동 789-1', '02-3456-7890', '건강한 도시락을 드립니다.',     NULL,                0.0, 0,  0, 'TEMP_CLOSED', 0, NOW(), NOW());

-- =============================================
-- 8. SettlementAccount 더미 데이터
-- =============================================

INSERT INTO settlement_account (settlement_account_id, store_id, bank_name, account_number, account_holder, created_at, modified_at) VALUES
(1, 1, '국민은행', '123-456-789012', '박사장', NOW(), NOW()),
(2, 2, '신한은행', '234-567-890123', '최사장', NOW(), NOW());

-- =============================================
-- 9. ProductCategory 더미 데이터
-- =============================================

INSERT INTO product_category (product_category_id, store_id, name, display_order, is_active, created_at, modified_at) VALUES
(1, 1, '반찬류',   1, TRUE, NOW(), NOW()),
(2, 1, '국/찌개',  2, TRUE, NOW(), NOW()),
(3, 1, '밑반찬',   3, TRUE, NOW(), NOW()),
(4, 2, '커피',     1, TRUE, NOW(), NOW()),
(5, 2, '논커피',   2, TRUE, NOW(), NOW()),
(6, 2, '디저트',   3, TRUE, NOW(), NOW());

-- =============================================
-- 10. Product 더미 데이터
-- =============================================

INSERT INTO product (product_id, store_id, product_category_id, name, description, price, stock, product_type, view_count, status, version, created_at, modified_at) VALUES
-- 상점 1 상품 (반찬가게)
(1, 1, 1, '김치찌개',     '직접 담근 김치로 끓인 찌개입니다.',     8000.00, NULL, 'MENU', 120, 'ACTIVE', 0, NOW(), NOW()),
(2, 1, 1, '된장찌개',     '구수한 된장찌개입니다.',                 7000.00, NULL, 'MENU', 80,  'ACTIVE', 0, NOW(), NOW()),
(3, 1, 2, '깍두기 500g',  '아삭아삭 깍두기입니다.',                 5000.00, 50,   'SALE', 200, 'ACTIVE', 0, NOW(), NOW()),
(4, 1, 2, '배추김치 1kg', '매콤한 배추김치입니다.',                 12000.00, 30,  'SALE', 350, 'ACTIVE', 0, NOW(), NOW()),
(5, 1, 3, '도시락 예약',  '점심 도시락 예약 상품입니다.',           8500.00, 20,   'RESERVATION', 50, 'ACTIVE', 0, NOW(), NOW()),
-- 상점 2 상품 (카페)
(6, 2, 4, '아메리카노',   '진한 에스프레소 아메리카노입니다.',      4500.00, NULL, 'MENU', 500, 'ACTIVE', 0, NOW(), NOW()),
(7, 2, 4, '카페라떼',     '부드러운 우유와 에스프레소.',            5000.00, NULL, 'MENU', 300, 'ACTIVE', 0, NOW(), NOW()),
(8, 2, 5, '초코스무디',   '달콤한 초코 스무디입니다.',              5500.00, NULL, 'MENU', 150, 'ACTIVE', 0, NOW(), NOW()),
(9, 2, 6, '크로플',       '바삭한 크로플입니다.',                   4000.00, 30,   'SALE', 200, 'ACTIVE', 0, NOW(), NOW()),
(10,2, 6, '티라미수',     '부드러운 티라미수 케이크입니다.',        6000.00, 15,   'SALE', 100, 'SOLD_OUT', 0, NOW(), NOW());

-- =============================================
-- 11. ProductImage 더미 데이터
-- =============================================

INSERT INTO product_image (product_image_id, product_id, image_url, display_order, is_thumbnail, created_at, modified_at) VALUES
(1,  1, 'https://via.placeholder.com/400x300?text=김치찌개',      1, TRUE,  NOW(), NOW()),
(2,  2, 'https://via.placeholder.com/400x300?text=된장찌개',      1, TRUE,  NOW(), NOW()),
(3,  3, 'https://via.placeholder.com/400x300?text=깍두기',        1, TRUE,  NOW(), NOW()),
(4,  3, 'https://via.placeholder.com/400x300?text=깍두기-2',      2, FALSE, NOW(), NOW()),
(5,  4, 'https://via.placeholder.com/400x300?text=배추김치',      1, TRUE,  NOW(), NOW()),
(6,  5, 'https://via.placeholder.com/400x300?text=도시락예약',    1, TRUE,  NOW(), NOW()),
(7,  6, 'https://via.placeholder.com/400x300?text=아메리카노',    1, TRUE,  NOW(), NOW()),
(8,  7, 'https://via.placeholder.com/400x300?text=카페라떼',      1, TRUE,  NOW(), NOW()),
(9,  8, 'https://via.placeholder.com/400x300?text=초코스무디',    1, TRUE,  NOW(), NOW()),
(10, 9, 'https://via.placeholder.com/400x300?text=크로플',        1, TRUE,  NOW(), NOW()),
(11,10, 'https://via.placeholder.com/400x300?text=티라미수',      1, TRUE,  NOW(), NOW());

-- =============================================
-- 12. EventProduct 더미 데이터
-- =============================================

INSERT INTO event_product (event_product_id, product_id, event_price, event_stock, sold_count, start_at, end_at, status, version, created_at, modified_at) VALUES
-- 진행 중 이벤트 (깍두기 500g 할인)
(1, 3, 3500.00, 30, 5,  DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY),  'ACTIVE', 0, NOW(), NOW()),
-- 종료된 이벤트 (배추김치 할인)
(2, 4, 8000.00, 20, 20, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 'ENDED',  0, NOW(), NOW()),
-- 예정 이벤트 (크로플 할인)
(3, 9, 2800.00, 50, 0,  DATE_ADD(NOW(), INTERVAL 2 DAY),  DATE_ADD(NOW(), INTERVAL 9 DAY), 'ACTIVE', 0, NOW(), NOW());

-- =============================================
-- 13. StoreNotice 더미 데이터
-- =============================================

-- =============================================
-- 13. StoreNotice 더미 데이터
-- =============================================

INSERT INTO store_notice (
    notice_id,
    store_id,
    title,
    content,
    notice_type,
    is_pinned,
    is_active,
    created_at,
    modified_at
) VALUES
-- 상점 1 공지
(1, 1, '설 연휴 휴무 안내', '설 연휴 기간(1/28~1/30) 동안 휴무합니다.', 'CLOSED_TODAY', TRUE,  TRUE,  NOW(), NOW()),
(2, 1, '신메뉴 출시 안내',  '봄 신메뉴 나물반찬 세트가 출시되었습니다.',     'NORMAL',       FALSE, TRUE,  NOW(), NOW()),
(3, 1, '재고 소진 안내',    '오늘 배추김치가 모두 소진되었습니다.',          'SOLD_OUT',     FALSE, FALSE, NOW(), NOW()),

-- 상점 2 공지
(4, 2, '오픈 준비 중',      '곧 오픈할 예정입니다. 많은 기대 부탁드립니다.', 'NORMAL',       TRUE,  TRUE,  NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;
