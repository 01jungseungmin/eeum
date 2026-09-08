-- 관리자 게시글 목록은 중고·커뮤니티 전체를 created_at DESC로 페이지 조회한다.
-- 기존 인덱스는 지역/상태 조건이 선행되어 전체 최신순 정렬을 지원하지 못하므로 별도 인덱스를 둔다.
CREATE INDEX idx_used_product_admin_created ON used_product (created_at);
CREATE INDEX idx_community_post_admin_created ON community_post (created_at);
