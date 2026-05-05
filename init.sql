-- =============================================
-- 이음(Eeum) 프로젝트 초기 DB 스키마 v2
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- Account 도메인
-- =============================================

CREATE TABLE IF NOT EXISTS region (
    region_id    BIGINT       NOT NULL AUTO_INCREMENT,
    region_code  VARCHAR(20)  NOT NULL UNIQUE,
    si_do        VARCHAR(50)  NOT NULL,
    gun_gu       VARCHAR(50)  NOT NULL,
    dong         VARCHAR(50)  NOT NULL,
    radius       INT          NOT NULL,
    PRIMARY KEY (region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS location (
    location_id  BIGINT   NOT NULL AUTO_INCREMENT,
    region_id    BIGINT   NOT NULL UNIQUE,
    latitude     DOUBLE   NOT NULL,
    longitude    DOUBLE   NOT NULL,
    PRIMARY KEY (location_id),
    FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- primary_region_id: FK 없이 서비스 레이어에서 관리 (순환참조 방지)
CREATE TABLE IF NOT EXISTS account (
    account_id          BIGINT        NOT NULL AUTO_INCREMENT,
    primary_region_id   BIGINT,
    email               VARCHAR(255)  UNIQUE,
    password            VARCHAR(255),
    name                VARCHAR(100)  NOT NULL,
    provider            VARCHAR(20)   NOT NULL,
    provider_id         VARCHAR(255),
    profile_image_url   VARCHAR(500),
    nickname            VARCHAR(50)   UNIQUE,
    role                VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    email_verified      BOOLEAN       NOT NULL DEFAULT FALSE,
    fcm_token           VARCHAR(255),
    created_at          DATETIME      NOT NULL,
    modified_at         DATETIME      NOT NULL,
    deleted_at          DATETIME,
    PRIMARY KEY (account_id),
    INDEX idx_account_primary_region (primary_region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SDD 명세 기준: owner_info_id AUTO_INCREMENT PK 유지
CREATE TABLE IF NOT EXISTS owner_info (
    owner_info_id     BIGINT        NOT NULL AUTO_INCREMENT,
    account_id        BIGINT        NOT NULL UNIQUE,
    phone             VARCHAR(20)   NOT NULL,
    business_number   VARCHAR(50)   NOT NULL UNIQUE,
    approval_status   VARCHAR(20)   NOT NULL,
    rejection_reason  VARCHAR(255),
    created_at        DATETIME      NOT NULL,
    modified_at       DATETIME      NOT NULL,
    PRIMARY KEY (owner_info_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- is_primary: Account.primaryRegionId로 관리 → 컬럼 없음 (SDD 설계)
-- [수정] 중복 등록 방지 UNIQUE 추가
CREATE TABLE IF NOT EXISTS account_region (
    account_region_id  BIGINT    NOT NULL AUTO_INCREMENT,
    account_id         BIGINT    NOT NULL,
    region_id          BIGINT    NOT NULL,
    verified           BOOLEAN   NOT NULL DEFAULT FALSE,
    verified_at        DATETIME,
    created_at         DATETIME  NOT NULL,
    modified_at        DATETIME  NOT NULL,
    PRIMARY KEY (account_region_id),
    UNIQUE KEY uk_account_region (account_id, region_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_audit_log (
    audit_log_id    BIGINT         NOT NULL AUTO_INCREMENT,
    account_id      BIGINT         NOT NULL,
    action_type     VARCHAR(50)    NOT NULL,
    target_type     VARCHAR(50)    NOT NULL,
    target_id       BIGINT,
    parameters      TEXT,
    result          VARCHAR(20)    NOT NULL,
    failure_reason  VARCHAR(1000),
    created_at      DATETIME       NOT NULL,
    modified_at     DATETIME       NOT NULL,
    PRIMARY KEY (audit_log_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Category (공통) - Soft Delete (isActive)
-- =============================================

-- [수정] parent_id NULL 포함 중복 방지 → generated column 사용
CREATE TABLE IF NOT EXISTS category (
    category_id  BIGINT       NOT NULL AUTO_INCREMENT,
    type         VARCHAR(20)  NOT NULL,
    parent_id    BIGINT,
    parent_key   BIGINT GENERATED ALWAYS AS (IFNULL(parent_id, 0)) STORED,
    name         VARCHAR(50)  NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    depth        INT          NOT NULL DEFAULT 1,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL,
    modified_at  DATETIME     NOT NULL,
    deleted_at   DATETIME,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_type_parent_name (type, parent_key, name),
    FOREIGN KEY (parent_id) REFERENCES category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Store 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS store (
    store_id        BIGINT          NOT NULL AUTO_INCREMENT,
    account_id      BIGINT          NOT NULL UNIQUE,
    region_id       BIGINT          NOT NULL,
    category_id     BIGINT          NOT NULL,
    latitude        DOUBLE          NOT NULL,
    longitude       DOUBLE          NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    address         VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20)     NOT NULL,
    description     TEXT,
    business_hours  VARCHAR(255)    NOT NULL,
    rating          DOUBLE          NOT NULL DEFAULT 0.0,
    favorite_count  INT             NOT NULL DEFAULT 0,
    review_count    INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL,
    modified_at     DATETIME        NOT NULL,
    PRIMARY KEY (store_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (region_id) REFERENCES region(region_id),
    FOREIGN KEY (category_id) REFERENCES category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_notice (
    notice_id    BIGINT       NOT NULL AUTO_INCREMENT,
    store_id     BIGINT       NOT NULL,
    notice_type  VARCHAR(20)  NOT NULL,
    content      TEXT         NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL,
    modified_at  DATETIME     NOT NULL,
    PRIMARY KEY (notice_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_image (
    store_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    store_id        BIGINT        NOT NULL,
    image_url       VARCHAR(500)  NOT NULL,
    display_order   INT           NOT NULL DEFAULT 0,
    is_thumbnail    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME      NOT NULL,
    modified_at     DATETIME      NOT NULL,
    PRIMARY KEY (store_image_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductCategory: Soft Delete (isActive) + 조건부 Hard Delete
CREATE TABLE IF NOT EXISTS product_category (
    product_category_id  BIGINT       NOT NULL AUTO_INCREMENT,
    store_id             BIGINT       NOT NULL,
    name                 VARCHAR(50)  NOT NULL,
    display_order        INT          NOT NULL DEFAULT 0,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           DATETIME     NOT NULL,
    modified_at          DATETIME     NOT NULL,
    PRIMARY KEY (product_category_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product (
    product_id           BIGINT          NOT NULL AUTO_INCREMENT,
    store_id             BIGINT          NOT NULL,
    product_category_id  BIGINT          NOT NULL,
    name                 VARCHAR(100)    NOT NULL,
    description          TEXT            NOT NULL,
    price                DECIMAL(10,2)   NOT NULL,
    stock                INT,
    view_count           INT             NOT NULL DEFAULT 0,
    status               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           DATETIME        NOT NULL,
    modified_at          DATETIME        NOT NULL,
    PRIMARY KEY (product_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id),
    FOREIGN KEY (product_category_id) REFERENCES product_category(product_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_image (
    product_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    image_url         VARCHAR(500)  NOT NULL,
    display_order     INT           NOT NULL DEFAULT 0,
    is_thumbnail      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME      NOT NULL,
    modified_at       DATETIME      NOT NULL,
    PRIMARY KEY (product_image_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_option (
    product_option_id  BIGINT       NOT NULL AUTO_INCREMENT,
    product_id         BIGINT       NOT NULL,
    group_name         VARCHAR(50)  NOT NULL,
    selection_type     VARCHAR(20)  NOT NULL DEFAULT 'SINGLE',
    is_required        BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order      INT          NOT NULL DEFAULT 0,
    created_at         DATETIME     NOT NULL,
    modified_at        DATETIME     NOT NULL,
    PRIMARY KEY (product_option_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_option_item (
    product_option_item_id  BIGINT          NOT NULL AUTO_INCREMENT,
    product_option_id       BIGINT          NOT NULL,
    item_name               VARCHAR(50)     NOT NULL,
    additional_price        DECIMAL(10,2)   NOT NULL DEFAULT 0,
    is_default              BOOLEAN         NOT NULL DEFAULT FALSE,
    display_order           INT             NOT NULL DEFAULT 0,
    is_available            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME        NOT NULL,
    modified_at             DATETIME        NOT NULL,
    PRIMARY KEY (product_option_item_id),
    FOREIGN KEY (product_option_id) REFERENCES product_option(product_option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [수정] active_product_id GENERATED COLUMN + UNIQUE 추가 (SDD 명세 반영)
CREATE TABLE IF NOT EXISTS event_product (
    event_product_id   BIGINT       NOT NULL AUTO_INCREMENT,
    product_id         BIGINT       NOT NULL,
    active_product_id  BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN product_id ELSE NULL END
    ) STORED,
    discount_rate      INT          NOT NULL,
    stock              INT,
    start_date         DATETIME     NOT NULL,
    end_date           DATETIME     NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME     NOT NULL,
    modified_at        DATETIME     NOT NULL,
    PRIMARY KEY (event_product_id),
    UNIQUE KEY uk_event_product_active (active_product_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Order 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS orders (
    order_id       BIGINT          NOT NULL AUTO_INCREMENT,
    account_id     BIGINT          NOT NULL,
    store_id       BIGINT          NOT NULL,
    total_price    DECIMAL(10,2)   NOT NULL,
    order_number   VARCHAR(50)     NOT NULL UNIQUE,
    version        BIGINT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)     NOT NULL,
    paid_at        DATETIME,
    cancelled_at   DATETIME,
    cancel_reason  VARCHAR(500),
    created_at     DATETIME        NOT NULL,
    modified_at    DATETIME        NOT NULL,
    PRIMARY KEY (order_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [수정] selected_options NULL 허용 (옵션 없는 상품도 주문 가능)
-- [수정] product_id / event_product_id 둘 중 하나만 허용 CHECK 추가
CREATE TABLE IF NOT EXISTS order_item (
    order_item_id     BIGINT          NOT NULL AUTO_INCREMENT,
    order_id          BIGINT          NOT NULL,
    product_id        BIGINT,
    event_product_id  BIGINT,
    product_name      VARCHAR(100)    NOT NULL,
    selected_options  TEXT,
    quantity          INT             NOT NULL,
    price             DECIMAL(10,2)   NOT NULL,
    PRIMARY KEY (order_item_id),
    CHECK (
        (product_id IS NOT NULL AND event_product_id IS NULL)
        OR
        (product_id IS NULL AND event_product_id IS NOT NULL)
    ),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id),
    FOREIGN KEY (event_product_id) REFERENCES event_product(event_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment (
    payment_id           BIGINT          NOT NULL AUTO_INCREMENT,
    order_id             BIGINT          NOT NULL UNIQUE,
    account_id           BIGINT          NOT NULL,
    portone_payment_id   VARCHAR(100)    NOT NULL UNIQUE,
    idempotency_key      VARCHAR(100)    NOT NULL UNIQUE,
    amount               DECIMAL(10,2)   NOT NULL,
    status               VARCHAR(20)     NOT NULL,
    pg_provider          VARCHAR(50),
    payment_method       VARCHAR(20)     NOT NULL,
    paid_at              DATETIME,
    cancelled_at         DATETIME,
    fail_reason          VARCHAR(500),
    created_at           DATETIME        NOT NULL,
    modified_at          DATETIME        NOT NULL,
    PRIMARY KEY (payment_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reservation (
    reservation_id    BIGINT          NOT NULL AUTO_INCREMENT,
    account_id        BIGINT          NOT NULL,
    store_id          BIGINT          NOT NULL,
    product_id        BIGINT,
    order_id          BIGINT,
    reserved_at       DATETIME        NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    reservation_type  VARCHAR(20)     NOT NULL,
    total_price       DECIMAL(10,2),
    capacity          INT,
    version           BIGINT          NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL,
    modified_at       DATETIME        NOT NULL,
    PRIMARY KEY (reservation_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart (
    cart_id      BIGINT    NOT NULL AUTO_INCREMENT,
    account_id   BIGINT    NOT NULL UNIQUE,
    store_id     BIGINT    NOT NULL,
    created_at   DATETIME  NOT NULL,
    modified_at  DATETIME  NOT NULL,
    PRIMARY KEY (cart_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [수정] product_id / event_product_id NULL 포함 UNIQUE → generated column 사용
-- [수정] product_id / event_product_id 둘 중 하나만 허용 CHECK 추가
CREATE TABLE IF NOT EXISTS cart_item (
    cart_item_id             BIGINT          NOT NULL AUTO_INCREMENT,
    cart_id                  BIGINT          NOT NULL,
    product_id               BIGINT,
    event_product_id         BIGINT,
    product_key              BIGINT GENERATED ALWAYS AS (IFNULL(product_id, -1)) STORED,
    event_product_key        BIGINT GENERATED ALWAYS AS (IFNULL(event_product_id, -1)) STORED,
    selected_option_item_ids TEXT,
    options_total_price      DECIMAL(10,2)   NOT NULL DEFAULT 0,
    selected_options_hash    VARCHAR(64)     NOT NULL,
    quantity                 INT             NOT NULL,
    price                    DECIMAL(10,2)   NOT NULL,
    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uk_cart_item_product_option (
        cart_id,
        product_key,
        event_product_key,
        selected_options_hash
    ),
    CHECK (
        (product_id IS NOT NULL AND event_product_id IS NULL)
        OR
        (product_id IS NULL AND event_product_id IS NOT NULL)
    ),
    FOREIGN KEY (cart_id) REFERENCES cart(cart_id),
    FOREIGN KEY (product_id) REFERENCES product(product_id),
    FOREIGN KEY (event_product_id) REFERENCES event_product(event_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Store Review 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS store_review (
    store_review_id  BIGINT    NOT NULL AUTO_INCREMENT,
    store_id         BIGINT    NOT NULL,
    account_id       BIGINT    NOT NULL,
    order_id         BIGINT    NOT NULL UNIQUE,
    rating           INT       NOT NULL,
    content          TEXT      NOT NULL,
    created_at       DATETIME  NOT NULL,
    modified_at      DATETIME  NOT NULL,
    PRIMARY KEY (store_review_id),
    FOREIGN KEY (store_id) REFERENCES store(store_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_review_image (
    store_review_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    store_review_id        BIGINT        NOT NULL,
    image_url              VARCHAR(500)  NOT NULL,
    display_order          INT           NOT NULL DEFAULT 0,
    is_thumbnail           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             DATETIME      NOT NULL,
    modified_at            DATETIME      NOT NULL,
    PRIMARY KEY (store_review_image_id),
    FOREIGN KEY (store_review_id) REFERENCES store_review(store_review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_review_reply (
    store_reply_id   BIGINT    NOT NULL AUTO_INCREMENT,
    store_review_id  BIGINT    NOT NULL UNIQUE,
    account_id       BIGINT    NOT NULL,
    content          TEXT      NOT NULL,
    created_at       DATETIME  NOT NULL,
    modified_at      DATETIME  NOT NULL,
    PRIMARY KEY (store_reply_id),
    FOREIGN KEY (store_review_id) REFERENCES store_review(store_review_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- UsedProduct 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS used_product (
    used_product_id     BIGINT          NOT NULL AUTO_INCREMENT,
    account_id          BIGINT          NOT NULL,
    buyer_id            BIGINT,
    region_id           BIGINT          NOT NULL,
    category_id         BIGINT          NOT NULL,
    title               VARCHAR(100)    NOT NULL,
    description         TEXT            NOT NULL,
    price               DECIMAL(10,2)   NOT NULL,
    trade_location      VARCHAR(255),
    view_count          INT             NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL,
    favorite_count      INT             NOT NULL DEFAULT 0,
    is_buyer_confirmed  BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    modified_at         DATETIME        NOT NULL,
    PRIMARY KEY (used_product_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (buyer_id) REFERENCES account(account_id),
    FOREIGN KEY (region_id) REFERENCES region(region_id),
    FOREIGN KEY (category_id) REFERENCES category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS used_product_image (
    used_product_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    used_product_id        BIGINT        NOT NULL,
    image_url              VARCHAR(500)  NOT NULL,
    display_order          INT           NOT NULL DEFAULT 0,
    is_thumbnail           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             DATETIME      NOT NULL,
    modified_at            DATETIME      NOT NULL,
    PRIMARY KEY (used_product_image_id),
    FOREIGN KEY (used_product_id) REFERENCES used_product(used_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS used_product_review (
    used_product_review_id  BIGINT    NOT NULL AUTO_INCREMENT,
    used_product_id         BIGINT    NOT NULL UNIQUE,
    account_id              BIGINT    NOT NULL,
    rating                  INT       NOT NULL,
    content                 TEXT      NOT NULL,
    created_at              DATETIME  NOT NULL,
    modified_at             DATETIME  NOT NULL,
    PRIMARY KEY (used_product_review_id),
    FOREIGN KEY (used_product_id) REFERENCES used_product(used_product_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Community 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS community_post (
    community_post_id  BIGINT        NOT NULL AUTO_INCREMENT,
    account_id         BIGINT        NOT NULL,
    category_id        BIGINT        NOT NULL,
    region_id          BIGINT        NOT NULL,
    title              VARCHAR(100)  NOT NULL,
    content            TEXT          NOT NULL,
    view_count         INT           NOT NULL DEFAULT 0,
    like_count         INT           NOT NULL DEFAULT 0,
    comment_count      INT           NOT NULL DEFAULT 0,
    created_at         DATETIME      NOT NULL,
    modified_at        DATETIME      NOT NULL,
    PRIMARY KEY (community_post_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (category_id) REFERENCES category(category_id),
    FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_image (
    community_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    community_post_id   BIGINT        NOT NULL,
    image_url           VARCHAR(500)  NOT NULL,
    display_order       INT           NOT NULL DEFAULT 0,
    is_thumbnail        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          DATETIME      NOT NULL,
    modified_at         DATETIME      NOT NULL,
    PRIMARY KEY (community_image_id),
    FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CommunityComment: Soft Delete (isDeleted) - SDD 6.6.2
CREATE TABLE IF NOT EXISTS community_comment (
    community_comment_id  BIGINT    NOT NULL AUTO_INCREMENT,
    community_post_id     BIGINT    NOT NULL,
    account_id            BIGINT    NOT NULL,
    content               TEXT      NOT NULL,
    is_deleted            BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at            DATETIME  NOT NULL,
    modified_at           DATETIME  NOT NULL,
    PRIMARY KEY (community_comment_id),
    FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_comment_reply (
    community_comment_reply_id  BIGINT    NOT NULL AUTO_INCREMENT,
    community_comment_id        BIGINT    NOT NULL,
    account_id                  BIGINT    NOT NULL,
    content                     TEXT      NOT NULL,
    created_at                  DATETIME  NOT NULL,
    modified_at                 DATETIME  NOT NULL,
    PRIMARY KEY (community_comment_reply_id),
    FOREIGN KEY (community_comment_id) REFERENCES community_comment(community_comment_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_like (
    post_like_id       BIGINT    NOT NULL AUTO_INCREMENT,
    account_id         BIGINT    NOT NULL,
    community_post_id  BIGINT    NOT NULL,
    PRIMARY KEY (post_like_id),
    UNIQUE KEY uk_post_like (account_id, community_post_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id),
    FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Chat 도메인
-- =============================================

CREATE TABLE IF NOT EXISTS chat_room (
    chat_room_id     BIGINT        NOT NULL AUTO_INCREMENT,
    created_by       BIGINT        NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    ref_type         VARCHAR(20),
    ref_id           BIGINT,
    name             VARCHAR(100),
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    last_message_at  DATETIME,
    created_at       DATETIME      NOT NULL,
    modified_at      DATETIME      NOT NULL,
    PRIMARY KEY (chat_room_id),
    FOREIGN KEY (created_by) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [수정] 같은 채팅방 중복 참여 방지 UNIQUE 추가
CREATE TABLE IF NOT EXISTS chat_participant (
    chat_participant_id  BIGINT        NOT NULL AUTO_INCREMENT,
    chat_room_id         BIGINT        NOT NULL,
    account_id           BIGINT        NOT NULL,
    last_read_time       DATETIME,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    joined_at            DATETIME      NOT NULL,
    left_at              DATETIME,
    created_at           DATETIME      NOT NULL,
    modified_at          DATETIME      NOT NULL,
    PRIMARY KEY (chat_participant_id),
    UNIQUE KEY uk_chat_participant (chat_room_id, account_id),
    FOREIGN KEY (chat_room_id) REFERENCES chat_room(chat_room_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ChatMessage: Soft Delete (isDeleted) - SDD 6.6.2
CREATE TABLE IF NOT EXISTS chat_message (
    chat_message_id  BIGINT        NOT NULL AUTO_INCREMENT,
    chat_room_id     BIGINT        NOT NULL,
    account_id       BIGINT        NOT NULL,
    content          TEXT,
    image_url        VARCHAR(500),
    message_type     VARCHAR(20)   NOT NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    sent_at          DATETIME      NOT NULL,
    created_at       DATETIME      NOT NULL,
    modified_at      DATETIME      NOT NULL,
    PRIMARY KEY (chat_message_id),
    INDEX idx_chat_message_sent_at (sent_at),
    FOREIGN KEY (chat_room_id) REFERENCES chat_room(chat_room_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Common 도메인 - Hard Delete (SDD 6.6.2)
-- =============================================

CREATE TABLE IF NOT EXISTS favorite (
    favorite_id  BIGINT        NOT NULL AUTO_INCREMENT,
    account_id   BIGINT        NOT NULL,
    ref_type     VARCHAR(20)   NOT NULL,
    ref_id       BIGINT        NOT NULL,
    created_at   DATETIME      NOT NULL,
    modified_at  DATETIME      NOT NULL,
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_favorite (account_id, ref_type, ref_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification (
    notification_id  BIGINT        NOT NULL AUTO_INCREMENT,
    account_id       BIGINT        NOT NULL,
    type             VARCHAR(50)   NOT NULL,
    title            VARCHAR(100)  NOT NULL,
    content          VARCHAR(500)  NOT NULL,
    ref_type         VARCHAR(30),
    ref_id           BIGINT,
    link_url         VARCHAR(500),
    is_read          BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at          DATETIME,
    created_at       DATETIME      NOT NULL,
    modified_at      DATETIME      NOT NULL,
    PRIMARY KEY (notification_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_settings (
    notification_settings_id  BIGINT    NOT NULL AUTO_INCREMENT,
    account_id                BIGINT    NOT NULL UNIQUE,
    order_enabled             BOOLEAN   NOT NULL DEFAULT TRUE,
    reservation_enabled       BOOLEAN   NOT NULL DEFAULT TRUE,
    chat_enabled              BOOLEAN   NOT NULL DEFAULT TRUE,
    community_enabled         BOOLEAN   NOT NULL DEFAULT TRUE,
    store_review_enabled      BOOLEAN   NOT NULL DEFAULT TRUE,
    used_product_enabled      BOOLEAN   NOT NULL DEFAULT TRUE,
    system_enabled            BOOLEAN   NOT NULL DEFAULT TRUE,
    marketing_enabled         BOOLEAN   NOT NULL DEFAULT FALSE,
    marketing_agreed_at       DATETIME,
    created_at                DATETIME  NOT NULL,
    modified_at               DATETIME  NOT NULL,
    PRIMARY KEY (notification_settings_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inquiry (
    inquiry_id    BIGINT        NOT NULL AUTO_INCREMENT,
    account_id    BIGINT        NOT NULL,
    inquiry_type  VARCHAR(20)   NOT NULL,
    target_id     BIGINT,
    category      VARCHAR(30)   NOT NULL,
    title         VARCHAR(100)  NOT NULL,
    content       TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    answered_at   DATETIME,
    closed_at     DATETIME,
    created_at    DATETIME      NOT NULL,
    modified_at   DATETIME      NOT NULL,
    PRIMARY KEY (inquiry_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inquiry_reply (
    inquiry_reply_id  BIGINT    NOT NULL AUTO_INCREMENT,
    inquiry_id        BIGINT    NOT NULL UNIQUE,
    account_id        BIGINT    NOT NULL,
    content           TEXT      NOT NULL,
    created_at        DATETIME  NOT NULL,
    modified_at       DATETIME  NOT NULL,
    PRIMARY KEY (inquiry_reply_id),
    FOREIGN KEY (inquiry_id) REFERENCES inquiry(inquiry_id),
    FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inquiry_image (
    inquiry_image_id  BIGINT        NOT NULL AUTO_INCREMENT,
    inquiry_id        BIGINT        NOT NULL,
    image_url         VARCHAR(500)  NOT NULL,
    display_order     INT           NOT NULL DEFAULT 0,
    is_thumbnail      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME      NOT NULL,
    modified_at       DATETIME      NOT NULL,
    PRIMARY KEY (inquiry_image_id),
    FOREIGN KEY (inquiry_id) REFERENCES inquiry(inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
