-- EEUM Flyway V1 baseline
-- Source of truth: GitHub develop branch as of 2026-09-02
--   1) root init.sql
--   2) backend/db/manual/*.sql through 2026-09-02
--   3) current JPA entities / BaseEntity / ImageBase
--
-- IMPORTANT
-- - This migration is for a NEW / EMPTY MySQL 8 database.
-- - An existing development database must be enrolled with Flyway baseline instead of executing V1 on top of it.
-- - Planned settlement/payment changes discussed after this baseline are intentionally NOT included here.
-- - EnumType.STRING columns use MySQL native ENUM values to match the current Hibernate 7/MySQL mapping.
--   Planned settlement/payment schema extensions belong in V2+.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =========================================================
-- Account / Region
-- =========================================================

CREATE TABLE region (
    region_id      BIGINT       NOT NULL AUTO_INCREMENT,
    region_code    VARCHAR(20)  NOT NULL,
    si_do          VARCHAR(50)  NOT NULL,
    gun_gu         VARCHAR(50)  NOT NULL,
    dong           VARCHAR(50)  NOT NULL,
    radius         INT          NOT NULL,
    PRIMARY KEY (region_id),
    CONSTRAINT uk_region_code UNIQUE (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE category (
    category_id    BIGINT       NOT NULL AUTO_INCREMENT,
    type           ENUM('COMMUNITY','STORE','USED')  NOT NULL,
    parent_id      BIGINT       NULL,
    parent_scope   BIGINT       NOT NULL DEFAULT 0,
    name           VARCHAR(100) NOT NULL,
    display_order  INT          NOT NULL,
    depth          INT          NOT NULL,
    is_active      BIT(1)       NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    modified_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT uk_category_type_parent_scope_name
        UNIQUE (type, parent_scope, name),
    KEY idx_category_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account (
    account_id         BIGINT       NOT NULL AUTO_INCREMENT,
    primary_region_id  BIGINT       NULL,
    email              VARCHAR(255) NULL,
    password           VARCHAR(255) NULL,
    name               VARCHAR(100) NOT NULL,
    phone              VARCHAR(20)  NOT NULL,
    provider           ENUM('KAKAO','LOCAL','NAVER')  NOT NULL,
    provider_id        VARCHAR(255) NULL,
    profile_image_url  VARCHAR(500) NULL,
    nickname           VARCHAR(50)  NULL,
    role               ENUM('ROLE_ADMIN','ROLE_OWNER','ROLE_USER')  NOT NULL,
    status             ENUM('ACTIVE','PENDING','SUSPENDED','WITHDRAWN')  NOT NULL,
    email_verified     BIT(1)       NOT NULL,
    fcm_token          VARCHAR(255) NULL,
    anonymized_at      DATETIME(6)  NULL,
    deleted_at         DATETIME(6)  NULL,
    token_version      BIGINT       NOT NULL DEFAULT 0,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL,
    modified_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_email UNIQUE (email),
    CONSTRAINT uk_account_nickname UNIQUE (nickname),
    CONSTRAINT uk_account_provider UNIQUE (provider, provider_id),
    KEY idx_account_primary_region (primary_region_id),
    KEY idx_account_anonymize_target (status, anonymized_at, deleted_at, account_id)
    -- primary_region_id intentionally has no FK because Account manages it as a scalar ID.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE location (
    location_id  BIGINT NOT NULL AUTO_INCREMENT,
    region_id    BIGINT NOT NULL,
    latitude     DOUBLE NOT NULL,
    longitude    DOUBLE NOT NULL,
    PRIMARY KEY (location_id),
    CONSTRAINT uk_location_region UNIQUE (region_id),
    CONSTRAINT fk_location_region
        FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE owner_info (
    owner_info_id       BIGINT       NOT NULL AUTO_INCREMENT,
    account_id          BIGINT       NOT NULL,
    business_number     VARCHAR(50)  NOT NULL,
    opening_date        DATE         NOT NULL,
    approval_status     ENUM('APPROVED','PENDING','REJECTED')  NOT NULL,
    rejection_reason    VARCHAR(255) NULL,
    review_requested_at DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    modified_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (owner_info_id),
    CONSTRAINT uk_owner_info_account UNIQUE (account_id),
    CONSTRAINT uk_owner_info_business_number UNIQUE (business_number),
    CONSTRAINT fk_owner_info_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_region (
    account_region_id BIGINT      NOT NULL AUTO_INCREMENT,
    account_id        BIGINT      NOT NULL,
    region_id         BIGINT      NOT NULL,
    verified          BIT(1)      NOT NULL,
    verified_at       DATETIME(6) NULL,
    created_at        DATETIME(6) NOT NULL,
    modified_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (account_region_id),
    CONSTRAINT uk_account_region UNIQUE (account_id, region_id),
    CONSTRAINT fk_account_region_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_account_region_region
        FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Store
-- =========================================================

CREATE TABLE store (
    store_id                   BIGINT       NOT NULL AUTO_INCREMENT,
    account_id                 BIGINT       NOT NULL,
    category_id                BIGINT       NULL,
    region_id                  BIGINT       NULL,
    latitude                   DOUBLE       NULL,
    longitude                  DOUBLE       NULL,
    name                       VARCHAR(100) NOT NULL,
    address                    VARCHAR(255) NOT NULL,
    phone                      VARCHAR(20)  NOT NULL,
    description                TEXT         NULL,
    rating                     DOUBLE       NOT NULL,
    favorite_count             INT          NOT NULL,
    review_count               INT          NOT NULL,
    visit_reservation_enabled  BIT(1)       NOT NULL,
    status                     ENUM('CLOSED','OPEN','SUSPENDED','TEMP_CLOSED')  NOT NULL,
    version                    BIGINT       NOT NULL DEFAULT 0,
    created_at                 DATETIME(6)  NOT NULL,
    modified_at                DATETIME(6)  NOT NULL,
    PRIMARY KEY (store_id),
    CONSTRAINT uk_store_account UNIQUE (account_id),
    CONSTRAINT fk_store_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_store_category
        FOREIGN KEY (category_id) REFERENCES category(category_id),
    CONSTRAINT fk_store_region
        FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_business_hour (
    store_business_hour_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id               BIGINT      NOT NULL,
    day_of_week            ENUM('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
    is_closed              BIT(1)      NOT NULL,
    open_time              TIME        NULL,
    close_time             TIME        NULL,
    created_at             DATETIME(6) NOT NULL,
    modified_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (store_business_hour_id),
    CONSTRAINT fk_store_business_hour_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_image (
    store_image_id BIGINT        NOT NULL AUTO_INCREMENT,
    store_id       BIGINT        NOT NULL,
    image_url      VARCHAR(1000) NOT NULL,
    display_order  INT           NOT NULL,
    is_thumbnail   BIT(1)        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    modified_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (store_image_id),
    CONSTRAINT fk_store_image_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_account (
    settlement_account_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id              BIGINT      NOT NULL,
    bank_name             VARCHAR(50) NOT NULL,
    account_number        VARCHAR(50) NOT NULL,
    account_holder        VARCHAR(50) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    modified_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (settlement_account_id),
    CONSTRAINT uk_settlement_account_store UNIQUE (store_id),
    CONSTRAINT fk_settlement_account_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_notice (
    notice_id      BIGINT       NOT NULL AUTO_INCREMENT,
    store_id       BIGINT       NOT NULL,
    title          VARCHAR(100) NOT NULL,
    content        TEXT         NOT NULL,
    is_pinned      BIT(1)       NOT NULL,
    is_active      BIT(1)       NOT NULL,
    notice_type    ENUM('CLOSED_TODAY','NORMAL','SOLD_OUT')  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    modified_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (notice_id),
    CONSTRAINT fk_store_notice_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Reservation support
-- =========================================================

CREATE TABLE store_table (
    store_table_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id       BIGINT      NOT NULL,
    capacity       INT         NOT NULL,
    table_name     VARCHAR(50) NOT NULL,
    active         BIT(1)      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    modified_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (store_table_id),
    KEY idx_store_table_store_active (store_id, active),
    KEY idx_store_table_store_capacity (store_id, capacity),
    CONSTRAINT fk_store_table_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_visit_reservation_setting (
    setting_id                    BIGINT      NOT NULL AUTO_INCREMENT,
    store_id                      BIGINT      NOT NULL,
    enabled                       BIT(1)      NOT NULL,
    slot_interval_minutes         INT         NOT NULL,
    same_day_reservation_allowed  BIT(1)      NOT NULL,
    cancel_deadline_minutes       INT         NOT NULL,
    start_time                    TIME        NOT NULL,
    end_time                      TIME        NOT NULL,
    version                       BIGINT      NOT NULL DEFAULT 0,
    created_at                    DATETIME(6) NOT NULL,
    modified_at                   DATETIME(6) NOT NULL,
    PRIMARY KEY (setting_id),
    CONSTRAINT uk_store_visit_reservation_setting_store UNIQUE (store_id),
    CONSTRAINT fk_store_visit_reservation_setting_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE visit_reservation_time_slot (
    time_slot_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id     BIGINT      NOT NULL,
    slot_date    DATE        NOT NULL,
    slot_time    TIME        NOT NULL,
    enabled      BIT(1)      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (time_slot_id),
    CONSTRAINT uk_visit_reservation_time_slot_store_date_time
        UNIQUE (store_id, slot_date, slot_time),
    KEY idx_visit_reservation_time_slot_store_date (store_id, slot_date),
    CONSTRAINT fk_visit_reservation_time_slot_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Product
-- =========================================================

CREATE TABLE product_category (
    product_category_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id            BIGINT      NOT NULL,
    name                VARCHAR(50) NOT NULL,
    display_order       INT         NOT NULL,
    is_active           BIT(1)      NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    modified_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (product_category_id),
    CONSTRAINT fk_product_category_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product (
    product_id          BIGINT        NOT NULL AUTO_INCREMENT,
    store_id            BIGINT        NOT NULL,
    product_category_id BIGINT        NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    description         TEXT          NULL,
    price               DECIMAL(10,2) NOT NULL,
    stock               INT           NULL,
    product_type        ENUM('MENU','PREORDER','SALE')   NOT NULL,
    view_count          INT           NOT NULL,
    status              ENUM('ACTIVE','INACTIVE','SOLD_OUT')   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    modified_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT fk_product_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_product_category
        FOREIGN KEY (product_category_id) REFERENCES product_category(product_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_image (
    product_image_id BIGINT        NOT NULL AUTO_INCREMENT,
    product_id       BIGINT        NOT NULL,
    image_url        VARCHAR(1000) NOT NULL,
    display_order    INT           NOT NULL,
    is_thumbnail     BIT(1)        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    modified_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (product_image_id),
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_option (
    product_option_id BIGINT      NOT NULL AUTO_INCREMENT,
    product_id        BIGINT      NOT NULL,
    group_name        VARCHAR(50) NOT NULL,
    selection_type    ENUM('MULTIPLE','SINGLE') NOT NULL,
    is_required       BIT(1)      NOT NULL,
    display_order     INT         NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    modified_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (product_option_id),
    CONSTRAINT fk_product_option_product
        FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_option_item (
    product_option_item_id BIGINT        NOT NULL AUTO_INCREMENT,
    product_option_id      BIGINT        NOT NULL,
    item_name              VARCHAR(50)   NOT NULL,
    additional_price       DECIMAL(10,2) NOT NULL,
    is_default             BIT(1)        NOT NULL,
    display_order          INT           NOT NULL,
    is_available           BIT(1)        NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    modified_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (product_option_item_id),
    CONSTRAINT fk_product_option_item_option
        FOREIGN KEY (product_option_id) REFERENCES product_option(product_option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE event_product (
    event_product_id  BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    event_price       DECIMAL(10,2) NOT NULL,
    event_stock       INT           NOT NULL,
    sold_count        INT           NOT NULL,
    start_at          DATETIME(6)   NOT NULL,
    end_at            DATETIME(6)   NOT NULL,
    status            ENUM('ACTIVE','DELETED','ENDED')   NOT NULL,
    -- DB-only invariant retained from init.sql: one ACTIVE event per product.
    active_product_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN product_id ELSE NULL END
    ) STORED,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    modified_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (event_product_id),
    CONSTRAINT uk_event_product_active UNIQUE (active_product_id),
    CONSTRAINT fk_event_product_product
        FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Order / Payment / Cart (current develop, before settlement expansion)
-- =========================================================

CREATE TABLE orders (
    order_id             BIGINT        NOT NULL AUTO_INCREMENT,
    account_id           BIGINT        NOT NULL,
    store_id             BIGINT        NOT NULL,
    order_number         VARCHAR(50)   NOT NULL,
    order_type           ENUM('PREORDER','SALE')   NOT NULL,
    status               ENUM('CANCELLED','COMPLETED','CONFIRMED','EXPIRED','PAID','PENDING','READY')   NOT NULL,
    total_price          DECIMAL(10,2) NOT NULL,
    pickup_scheduled_at  DATETIME(6)   NULL,
    request_message      VARCHAR(500)  NULL,
    paid_at              DATETIME(6)   NULL,
    confirmed_at         DATETIME(6)   NULL,
    ready_at             DATETIME(6)   NULL,
    completed_at         DATETIME(6)   NULL,
    cancelled_at         DATETIME(6)   NULL,
    cancel_reason        VARCHAR(500)  NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           DATETIME(6)   NOT NULL,
    modified_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (order_id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_orders_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_item (
    order_item_id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_id                 BIGINT        NOT NULL,
    product_id               BIGINT        NULL,
    event_product_id         BIGINT        NULL,
    product_type             ENUM('MENU','PREORDER','SALE')   NOT NULL,
    product_name             VARCHAR(100)  NOT NULL,
    thumbnail_url            VARCHAR(1000) NULL,
    selected_option_item_ids VARCHAR(1000) NULL,
    selected_options_text    TEXT          NULL,
    quantity                 INT           NOT NULL,
    base_price               DECIMAL(10,2) NOT NULL,
    options_total_price      DECIMAL(10,2) NOT NULL,
    unit_price               DECIMAL(10,2) NOT NULL,
    line_total_price         DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (order_item_id),
    CONSTRAINT chk_order_item_product_source CHECK (
        (product_id IS NOT NULL AND event_product_id IS NULL)
        OR (product_id IS NULL AND event_product_id IS NOT NULL)
    ),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
    -- product_id / event_product_id are scalar snapshots in the current entity, so no FK is created.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment (
    payment_id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id            BIGINT        NOT NULL,
    account_id          BIGINT        NOT NULL,
    portone_payment_id  VARCHAR(100)  NULL,
    idempotency_key     VARCHAR(100)  NOT NULL,
    amount              DECIMAL(10,2) NOT NULL,
    status              ENUM('CANCELLED','FAILED','NOT_PAID','PAID','PENDING','REFUNDED')   NOT NULL,
    pg_provider         VARCHAR(50)   NULL,
    payment_method      ENUM('CARD','CASH_ON_SITE','EASY_PAY','TRANSFER','VIRTUAL_ACCOUNT')   NOT NULL,
    paid_at             DATETIME(6)   NULL,
    cancelled_at        DATETIME(6)   NULL,
    fail_reason         VARCHAR(500)  NULL,
    refund_status       ENUM('APPROVED','REJECTED','REQUESTED')   NULL,
    refund_reason       VARCHAR(500)  NULL,
    refunded_at         DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    modified_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_portone_payment_id UNIQUE (portone_payment_id),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_payment_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart (
    cart_id      BIGINT      NOT NULL AUTO_INCREMENT,
    account_id   BIGINT      NOT NULL,
    store_id     BIGINT      NULL,
    created_at   DATETIME(6) NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (cart_id),
    CONSTRAINT uk_cart_account UNIQUE (account_id),
    CONSTRAINT fk_cart_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_cart_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_item (
    cart_item_id             BIGINT        NOT NULL AUTO_INCREMENT,
    cart_id                  BIGINT        NOT NULL,
    product_id               BIGINT        NULL,
    event_product_id         BIGINT        NULL,
    -- DB-only generated keys retained from init.sql so NULL values participate in uniqueness.
    product_key              BIGINT GENERATED ALWAYS AS (IFNULL(product_id, -1)) STORED,
    event_product_key        BIGINT GENERATED ALWAYS AS (IFNULL(event_product_id, -1)) STORED,
    selected_option_item_ids VARCHAR(1000) NULL,
    selected_options_text    TEXT          NULL,
    selected_options_hash    VARCHAR(64)   NOT NULL,
    options_total_price      DECIMAL(10,2) NOT NULL,
    quantity                 INT           NOT NULL,
    unit_price               DECIMAL(10,2) NOT NULL,
    created_at               DATETIME(6)   NOT NULL,
    modified_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (cart_item_id),
    CONSTRAINT uk_cart_item_product_option
        UNIQUE (cart_id, product_key, event_product_key, selected_options_hash),
    CONSTRAINT chk_cart_item_product_source CHECK (
        (product_id IS NOT NULL AND event_product_id IS NULL)
        OR (product_id IS NULL AND event_product_id IS NOT NULL)
    ),
    CONSTRAINT fk_cart_item_cart
        FOREIGN KEY (cart_id) REFERENCES cart(cart_id),
    CONSTRAINT fk_cart_item_product
        FOREIGN KEY (product_id) REFERENCES product(product_id),
    CONSTRAINT fk_cart_item_event_product
        FOREIGN KEY (event_product_id) REFERENCES event_product(event_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Visit reservation (current entity replacing legacy reservation shape)
-- =========================================================

CREATE TABLE reservation (
    visit_reservation_id BIGINT       NOT NULL AUTO_INCREMENT,
    store_id             BIGINT       NOT NULL,
    account_id           BIGINT       NOT NULL,
    visit_date           DATE         NOT NULL,
    visit_time           TIME         NOT NULL,
    party_size           INT          NOT NULL,
    request_message      VARCHAR(500) NULL,
    reject_reason        VARCHAR(500) NULL,
    store_table_id       BIGINT       NULL,
    reserved_start_at    DATETIME(6)  NULL,
    reserved_end_at      DATETIME(6)  NULL,
    status               ENUM('APPROVED','CANCELED','COMPLETED','PENDING','REJECTED')  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    modified_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (visit_reservation_id),
    CONSTRAINT uk_reservation_table_start
        UNIQUE (store_table_id, reserved_start_at),
    CONSTRAINT uk_reservation_account_store_start
        UNIQUE (account_id, store_id, reserved_start_at),
    KEY idx_visit_reservation_table_time_status
        (store_table_id, reserved_start_at, reserved_end_at, status),
    KEY idx_visit_reservation_store_date_time_status
        (store_id, visit_date, visit_time, status),
    KEY idx_visit_reservation_account_status
        (account_id, status),
    CONSTRAINT fk_reservation_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_reservation_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_reservation_store_table
        FOREIGN KEY (store_table_id) REFERENCES store_table(store_table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Store review
-- =========================================================

CREATE TABLE store_review (
    store_review_id      BIGINT      NOT NULL AUTO_INCREMENT,
    store_id             BIGINT      NOT NULL,
    account_id           BIGINT      NOT NULL,
    order_id             BIGINT      NULL,
    visit_reservation_id BIGINT      NULL,
    review_type          ENUM('ORDER','RESERVATION') NOT NULL,
    rating               INT         NOT NULL,
    content              TEXT        NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    modified_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (store_review_id),
    CONSTRAINT uk_store_review_order UNIQUE (order_id),
    CONSTRAINT uk_store_review_visit_reservation UNIQUE (visit_reservation_id),
    CONSTRAINT fk_store_review_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_store_review_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_store_review_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_store_review_reservation
        FOREIGN KEY (visit_reservation_id) REFERENCES reservation(visit_reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_review_image (
    store_review_image_id BIGINT        NOT NULL AUTO_INCREMENT,
    store_review_id       BIGINT        NOT NULL,
    image_url             VARCHAR(1000) NOT NULL,
    display_order         INT           NOT NULL,
    is_thumbnail          BIT(1)        NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    modified_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (store_review_image_id),
    CONSTRAINT fk_store_review_image_review
        FOREIGN KEY (store_review_id) REFERENCES store_review(store_review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE store_review_reply (
    store_reply_id  BIGINT      NOT NULL AUTO_INCREMENT,
    store_review_id BIGINT      NOT NULL,
    account_id      BIGINT      NOT NULL,
    content         TEXT        NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    modified_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (store_reply_id),
    CONSTRAINT uk_store_review_reply_review UNIQUE (store_review_id),
    CONSTRAINT fk_store_review_reply_review
        FOREIGN KEY (store_review_id) REFERENCES store_review(store_review_id),
    CONSTRAINT fk_store_review_reply_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Used product
-- =========================================================

CREATE TABLE used_product (
    used_product_id  BIGINT        NOT NULL AUTO_INCREMENT,
    account_id       BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL,
    region_id        BIGINT        NOT NULL,
    title            VARCHAR(100)  NOT NULL,
    content          TEXT          NOT NULL,
    price_type       ENUM('FIXED','FREE','NEGOTIABLE')   NOT NULL,
    price            DECIMAL(10,2) NULL,
    -- 공개되는 "대략" 위치다. 정확한 주소는 담지 않는다 — 확정 장소는 채팅에서 정한다.
    -- 이름·위도·경도는 셋 다 있거나 셋 다 없다 (UsedProduct.validateTradeLocation).
    -- 장소를 비우면 region_id가 "동네만 지정"을 담당한다.
    trade_location_name VARCHAR(255) NULL,
    trade_latitude   DOUBLE        NULL,
    trade_longitude  DOUBLE        NULL,
    -- 카카오 장소 ID. 지도에서 직접 찍은 핀은 값이 없으므로 nullable이다.
    trade_place_id   VARCHAR(50)   NULL,
    status           ENUM('RESERVED','SELLING','SOLD')   NOT NULL,
    buyer_account_id BIGINT        NULL,
    is_hidden        BIT(1)        NOT NULL,
    deleted_at       DATETIME(6)   NULL,
    view_count       INT           NOT NULL,
    favorite_count   INT           NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    modified_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (used_product_id),
    KEY idx_used_product_region_status (region_id, status, created_at),
    KEY idx_used_product_public_list (region_id, deleted_at, is_hidden, created_at),
    KEY idx_used_product_seller (account_id),
    CONSTRAINT fk_used_product_seller
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_used_product_category
        FOREIGN KEY (category_id) REFERENCES category(category_id),
    CONSTRAINT fk_used_product_region
        FOREIGN KEY (region_id) REFERENCES region(region_id),
    CONSTRAINT fk_used_product_buyer
        FOREIGN KEY (buyer_account_id) REFERENCES account(account_id),
    -- 장소명·좌표는 전부 있거나 전부 없다. place_id는 셋이 있을 때만 허용한다.
    CONSTRAINT chk_used_product_trade_location CHECK (
        (trade_location_name IS NOT NULL
            AND trade_latitude IS NOT NULL AND trade_longitude IS NOT NULL)
        OR (trade_location_name IS NULL
            AND trade_latitude IS NULL AND trade_longitude IS NULL
            AND trade_place_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE used_product_image (
    used_product_image_id BIGINT        NOT NULL AUTO_INCREMENT,
    used_product_id       BIGINT        NOT NULL,
    image_url             VARCHAR(1000) NOT NULL,
    display_order         INT           NOT NULL,
    is_thumbnail          BIT(1)        NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    modified_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (used_product_image_id),
    KEY idx_used_product_image_product (used_product_id, display_order),
    CONSTRAINT fk_used_product_image_product
        FOREIGN KEY (used_product_id) REFERENCES used_product(used_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE used_review (
    used_review_id       BIGINT      NOT NULL AUTO_INCREMENT,
    used_product_id      BIGINT      NOT NULL,
    reviewer_account_id  BIGINT      NOT NULL,
    rating               INT         NOT NULL,
    content              TEXT        NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    modified_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (used_review_id),
    CONSTRAINT uk_used_review_product_reviewer
        UNIQUE (used_product_id, reviewer_account_id),
    KEY idx_used_review_product (used_product_id, created_at),
    KEY idx_used_review_reviewer (reviewer_account_id, created_at),
    CONSTRAINT fk_used_review_product
        FOREIGN KEY (used_product_id) REFERENCES used_product(used_product_id),
    CONSTRAINT fk_used_review_reviewer
        FOREIGN KEY (reviewer_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 최종 확정 거래 장소. 채팅에서 오간 LOCATION 메시지는 "제안"이고,
-- 그중 양쪽이 합의한 하나가 여기 남는다. 거래(상품 × 구매자)당 한 건이다.
--
-- 키를 chat_room_id로 잡지 않는다 — ChatRoom은 deactivate()로 종료할 수 있고,
-- 종료되면 같은 (상품, 구매자) 조합으로 방이 새로 생긴다. 방에 매달면 한 번
-- 종료·재생성되는 순간 합의했던 약속이 끊긴다. 거래의 실제 식별자는 이 쌍이다.
--
CREATE TABLE used_trade_appointment (
    used_trade_appointment_id BIGINT       NOT NULL AUTO_INCREMENT,
    used_product_id           BIGINT       NOT NULL,
    buyer_account_id          BIGINT       NOT NULL,
    place_name                VARCHAR(255) NOT NULL,
    -- 카카오 장소 검색을 거치지 않고 지도에서 직접 찍은 핀은 주소·장소 ID가 없다.
    address                   VARCHAR(255) NULL,
    latitude                  DOUBLE       NOT NULL,
    longitude                 DOUBLE       NOT NULL,
    place_id                  VARCHAR(50)  NULL,
    created_at                DATETIME(6)  NOT NULL,
    modified_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (used_trade_appointment_id),
    -- 재조율은 새 행이 아니라 기존 행 갱신이다. 거래당 확정 장소는 언제나 하나다.
    CONSTRAINT uk_used_trade_appointment_trade
        UNIQUE (used_product_id, buyer_account_id),
    CONSTRAINT fk_used_trade_appointment_product
        FOREIGN KEY (used_product_id) REFERENCES used_product(used_product_id),
    CONSTRAINT fk_used_trade_appointment_buyer
        FOREIGN KEY (buyer_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Community
-- =========================================================

CREATE TABLE community_post (
    community_post_id BIGINT       NOT NULL AUTO_INCREMENT,
    account_id        BIGINT       NOT NULL,
    category_id       BIGINT       NOT NULL,
    region_id         BIGINT       NOT NULL,
    title             VARCHAR(200) NOT NULL,
    content           TEXT         NOT NULL,
    view_count        INT          NOT NULL,
    like_count        INT          NOT NULL,
    comment_count     INT          NOT NULL,
    is_hidden         BIT(1)       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    modified_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (community_post_id),
    CONSTRAINT fk_community_post_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_community_post_category
        FOREIGN KEY (category_id) REFERENCES category(category_id),
    CONSTRAINT fk_community_post_region
        FOREIGN KEY (region_id) REFERENCES region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_image (
    community_image_id BIGINT        NOT NULL AUTO_INCREMENT,
    community_post_id  BIGINT        NOT NULL,
    image_url          VARCHAR(1000) NOT NULL,
    display_order      INT           NOT NULL,
    is_thumbnail       BIT(1)        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    modified_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (community_image_id),
    CONSTRAINT fk_community_image_post
        FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_comment (
    community_comment_id BIGINT      NOT NULL AUTO_INCREMENT,
    community_post_id    BIGINT      NOT NULL,
    account_id           BIGINT      NOT NULL,
    parent_comment_id    BIGINT      NULL,
    content              TEXT        NOT NULL,
    like_count           INT         NOT NULL,
    is_deleted           BIT(1)      NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    modified_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (community_comment_id),
    CONSTRAINT fk_community_comment_post
        FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id),
    CONSTRAINT fk_community_comment_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_community_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES community_comment(community_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_post_like (
    community_post_like_id BIGINT      NOT NULL AUTO_INCREMENT,
    account_id             BIGINT      NOT NULL,
    community_post_id      BIGINT      NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    modified_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (community_post_like_id),
    CONSTRAINT uq_community_post_like_account_post
        UNIQUE (account_id, community_post_id),
    CONSTRAINT fk_community_post_like_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_community_post_like_post
        FOREIGN KEY (community_post_id) REFERENCES community_post(community_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_comment_like (
    community_comment_like_id BIGINT      NOT NULL AUTO_INCREMENT,
    account_id                BIGINT      NOT NULL,
    community_comment_id      BIGINT      NOT NULL,
    created_at                DATETIME(6) NOT NULL,
    modified_at               DATETIME(6) NOT NULL,
    PRIMARY KEY (community_comment_like_id),
    CONSTRAINT uq_community_comment_like_account_comment
        UNIQUE (account_id, community_comment_id),
    CONSTRAINT fk_community_comment_like_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_community_comment_like_comment
        FOREIGN KEY (community_comment_id) REFERENCES community_comment(community_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Chat
-- =========================================================

CREATE TABLE chat_room (
    chat_room_id      BIGINT       NOT NULL AUTO_INCREMENT,
    created_by        BIGINT       NOT NULL,
    type              ENUM('GROUP','GROUP_STREET','PRIVATE')  NOT NULL,
    ref_type          ENUM('COMMUNITY','NONE','STORE','USED_PRODUCT')  NULL,
    ref_id            BIGINT       NULL,
    name              VARCHAR(100) NULL,
    buyer_account_id  BIGINT       NULL,
    is_active         BIT(1)       NOT NULL,
    closed_at         DATETIME(6)  NULL,
    active_ref_key    VARCHAR(80) GENERATED ALWAYS AS (
        CASE
            WHEN is_active = 1 AND ref_type = 'STORE' AND ref_id IS NOT NULL
                THEN CONCAT(ref_type, ':', ref_id)
            WHEN is_active = 1 AND ref_type = 'USED_PRODUCT' AND ref_id IS NOT NULL
                 AND buyer_account_id IS NOT NULL
                THEN CONCAT(ref_type, ':', ref_id, ':', buyer_account_id)
        END
    ) STORED,
    last_message_at   DATETIME(6)  NULL,
    region_id         BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL,
    modified_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (chat_room_id),
    CONSTRAINT uk_chat_room_active_ref UNIQUE (active_ref_key),
    KEY idx_chat_room_ref (ref_type, ref_id, is_active),
    KEY idx_chat_room_public_list (region_id, is_active, last_message_at, chat_room_id),
    CONSTRAINT fk_chat_room_creator
        FOREIGN KEY (created_by) REFERENCES account(account_id),
    CONSTRAINT fk_chat_room_region
        FOREIGN KEY (region_id) REFERENCES region(region_id)
    -- buyer_account_id intentionally has no FK (current ChatRoom mapping/manual policy).
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_participant (
    chat_participant_id BIGINT      NOT NULL AUTO_INCREMENT,
    chat_room_id        BIGINT      NOT NULL,
    account_id          BIGINT      NOT NULL,
    last_read_time      DATETIME(6) NULL,
    status              ENUM('ACTIVE','LEFT') NOT NULL,
    joined_at           DATETIME(6) NOT NULL,
    left_at             DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL,
    modified_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (chat_participant_id),
    CONSTRAINT uk_chat_participant UNIQUE (chat_room_id, account_id),
    CONSTRAINT fk_chat_participant_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_room(chat_room_id),
    CONSTRAINT fk_chat_participant_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    chat_message_id BIGINT        NOT NULL AUTO_INCREMENT,
    chat_room_id    BIGINT        NOT NULL,
    account_id      BIGINT        NOT NULL,
    content         TEXT          NULL,
    image_url       VARCHAR(500)  NULL,
    message_type    ENUM('IMAGE','LOCATION','SYSTEM','TEXT')   NOT NULL,
    -- LOCATION 메시지 전용. 채팅은 참여자에게만 보이므로 글과 달리 정확한 주소를 담는다.
    -- 필수/배타 규칙은 ChatMessage의 타입별 정적 팩토리가 강제한다 (아래 CHECK는 보조).
    place_name      VARCHAR(255)  NULL,
    address         VARCHAR(255)  NULL,
    latitude        DOUBLE        NULL,
    longitude       DOUBLE        NULL,
    place_id        VARCHAR(50)   NULL,
    deleted_at      DATETIME(6)   NULL,
    sent_at         DATETIME(6)   NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    modified_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (chat_message_id),
    KEY idx_chat_message_room_sent (chat_room_id, sent_at, chat_message_id),
    CONSTRAINT fk_chat_message_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_room(chat_room_id),
    CONSTRAINT fk_chat_message_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    -- LOCATION은 장소명·좌표가 반드시 있고, 그 밖의 타입은 위치 컬럼이 비어 있어야 한다.
    CONSTRAINT chk_chat_message_location CHECK (
        (message_type = 'LOCATION'
            AND place_name IS NOT NULL AND latitude IS NOT NULL AND longitude IS NOT NULL)
        OR (message_type <> 'LOCATION'
            AND place_name IS NULL AND address IS NULL
            AND latitude IS NULL AND longitude IS NULL AND place_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Favorite
-- =========================================================

CREATE TABLE favorite (
    favorite_id  BIGINT      NOT NULL AUTO_INCREMENT,
    account_id   BIGINT      NOT NULL,
    ref_type     ENUM('STORE','USED_PRODUCT') NOT NULL,
    ref_id       BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (favorite_id),
    CONSTRAINT uk_favorite_account_ref UNIQUE (account_id, ref_type, ref_id),
    KEY idx_favorite_ref (ref_type, ref_id),
    KEY idx_favorite_account_type_created (account_id, ref_type, created_at, favorite_id),
    KEY idx_favorite_account_created (account_id, created_at, favorite_id),
    CONSTRAINT fk_favorite_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Notification
-- =========================================================

CREATE TABLE notification (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    account_id      BIGINT       NOT NULL,
    type            ENUM('CHAT_MESSAGE','COMMUNITY_ADMIN_ACTION','COMMUNITY_COMMENT','COMMUNITY_LIKE','COMMUNITY_REPLY','INQUIRY_ANSWERED','INQUIRY_SUBMITTED','MARKETING_EVENT','NEW_ORDER','NEW_RESERVATION','OPERATION_FAILURE_DETECTED','ORDER_STATUS_CHANGED','OWNER_APPLICATION_SUBMITTED','PAYMENT_COMPLETED','REPORT_SUBMITTED','RESERVATION_CANCELLED','RESERVATION_CONFIRMED','RESERVATION_REJECTED','RESERVATION_REMINDER','SETTLEMENT_COMPLETED','STOCK_WARNING','STORE_PRODUCT_RESTOCK','STORE_REVIEW','STORE_REVIEW_ADMIN_ACTION','STORE_REVIEW_REPLY','SYSTEM_NOTICE','USED_PRODUCT_INQUIRY','USED_REVIEW')  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    ref_type        ENUM('CHAT_ROOM','COMMUNITY_COMMENT','COMMUNITY_POST','INQUIRY','OPERATION_FAILURE','ORDER','OWNER_APPLICATION','PAYMENT','PRODUCT','REPORT','RESERVATION','SETTLEMENT','STORE','STORE_REVIEW','SYSTEM','USED_PRODUCT','USED_REVIEW')  NULL,
    ref_id          BIGINT       NULL,
    link_url        VARCHAR(500) NULL,
    is_read         BIT(1)       NOT NULL,
    read_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    modified_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    KEY idx_notification_account_read (account_id, is_read),
    CONSTRAINT fk_notification_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_settings (
    notification_settings_id      BIGINT      NOT NULL AUTO_INCREMENT,
    account_id                    BIGINT      NOT NULL,
    all_enabled                   BOOLEAN     NOT NULL DEFAULT TRUE,
    order_enabled                 BIT(1)      NOT NULL,
    reservation_enabled           BIT(1)      NOT NULL,
    chat_enabled                  BIT(1)      NOT NULL,
    community_enabled             BIT(1)      NOT NULL,
    store_review_enabled          BIT(1)      NOT NULL,
    used_product_enabled          BIT(1)      NOT NULL,
    system_enabled                BIT(1)      NOT NULL,
    stock_enabled                 BIT(1)      NOT NULL,
    settlement_enabled            BIT(1)      NOT NULL,
    marketing_enabled             BIT(1)      NOT NULL,
    marketing_agreed_at           DATETIME(6) NULL,
    dnd_enabled                   BIT(1)      NOT NULL,
    dnd_start_time                TIME        NOT NULL,
    dnd_end_time                  TIME        NOT NULL,
    order_email_enabled           BIT(1)      NOT NULL,
    chat_email_enabled            BIT(1)      NOT NULL,
    review_email_enabled          BIT(1)      NOT NULL,
    reservation_email_enabled     BIT(1)      NOT NULL,
    stock_email_enabled           BIT(1)      NOT NULL,
    settlement_email_enabled      BIT(1)      NOT NULL,
    order_sound_enabled           BIT(1)      NOT NULL,
    chat_sound_enabled            BIT(1)      NOT NULL,
    review_sound_enabled          BIT(1)      NOT NULL,
    reservation_sound_enabled     BIT(1)      NOT NULL,
    stock_sound_enabled           BIT(1)      NOT NULL,
    settlement_sound_enabled      BIT(1)      NOT NULL,
    created_at                    DATETIME(6) NOT NULL,
    modified_at                   DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_settings_id),
    CONSTRAINT uk_notification_settings_account UNIQUE (account_id),
    CONSTRAINT fk_notification_settings_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_outbox (
    outbox_id      BIGINT        NOT NULL AUTO_INCREMENT,
    event_type     VARCHAR(100)  NOT NULL,
    payload        LONGTEXT      NOT NULL,
    status         ENUM('DONE','FAILED','PENDING')   NOT NULL,
    attempt_count  INT           NOT NULL,
    last_error     VARCHAR(1000) NULL,
    processed_at   DATETIME(6)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    modified_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (outbox_id),
    KEY idx_outbox_status_created (status, created_at, outbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Inquiry
-- =========================================================

CREATE TABLE inquiry (
    inquiry_id   BIGINT       NOT NULL AUTO_INCREMENT,
    account_id   BIGINT       NOT NULL,
    store_id     BIGINT       NULL,
    target_type  ENUM('ADMIN','STORE')  NOT NULL,
    category     ENUM('ACCOUNT','ETC','ORDER','PAYMENT','REPORT','RESERVATION','STORE')  NOT NULL,
    status       ENUM('ANSWERED','CLOSED','PENDING')  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    secret       BIT(1)       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    modified_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (inquiry_id),
    CONSTRAINT fk_inquiry_account
        FOREIGN KEY (account_id) REFERENCES account(account_id),
    CONSTRAINT fk_inquiry_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inquiry_answer (
    answer_id      BIGINT      NOT NULL AUTO_INCREMENT,
    inquiry_id     BIGINT      NOT NULL,
    account_id     BIGINT      NOT NULL,
    writer_type    ENUM('ADMIN','OWNER') NOT NULL,
    content        TEXT        NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    modified_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (answer_id),
    CONSTRAINT uk_inquiry_answer_inquiry_id UNIQUE (inquiry_id),
    CONSTRAINT fk_inquiry_answer_inquiry
        FOREIGN KEY (inquiry_id) REFERENCES inquiry(inquiry_id),
    CONSTRAINT fk_inquiry_answer_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Report / Sanction / Operation failure
-- =========================================================

CREATE TABLE report (
    report_id                         BIGINT        NOT NULL AUTO_INCREMENT,
    account_id                        BIGINT        NOT NULL,
    target_type                       ENUM('ACCOUNT','COMMUNITY_COMMENT','COMMUNITY_POST','STORE','STORE_REVIEW','USED_PRODUCT')   NOT NULL,
    target_id                         BIGINT        NOT NULL,
    reason                            ENUM('ABUSE','ETC','FALSE_INFORMATION','FRAUD','INAPPROPRIATE_CONTENT','PERSONAL_INFORMATION','SPAM')   NOT NULL,
    content                           VARCHAR(1000) NULL,
    target_title_snapshot             VARCHAR(255)  NULL,
    target_content_snapshot           TEXT          NULL,
    target_owner_account_id_snapshot  BIGINT        NULL,
    status                            ENUM('DISMISSED','PENDING','REVIEWED')   NOT NULL,
    admin_note                        TEXT          NULL,
    admin_action                      ENUM('DELETE_COMMENT','DELETE_POST','DELETE_STORE_REVIEW','DISMISS','HIDE_POST','SUSPEND_AUTHOR','SUSPEND_STORE','WARN_AUTHOR')   NULL,
    processed_by_admin_id             BIGINT        NULL,
    action_target_account_id          BIGINT        NULL,
    version                           BIGINT        NOT NULL DEFAULT 0,
    created_at                        DATETIME(6)   NOT NULL,
    modified_at                       DATETIME(6)   NOT NULL,
    PRIMARY KEY (report_id),
    CONSTRAINT uk_report_reporter_target UNIQUE (account_id, target_type, target_id),
    KEY idx_report_status (status),
    KEY idx_report_target (target_type, target_id),
    CONSTRAINT fk_report_account
        FOREIGN KEY (account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sanction_history (
    sanction_history_id    BIGINT      NOT NULL AUTO_INCREMENT,
    target_type            ENUM('ACCOUNT','STORE') NOT NULL,
    target_id              BIGINT      NOT NULL,
    action                 ENUM('ACTIVATE','SUSPEND','WARN') NOT NULL,
    source                 ENUM('DIRECT_ADMIN','REPORT') NOT NULL,
    admin_note             TEXT        NULL,
    processed_by_admin_id  BIGINT      NULL,
    source_report_id       BIGINT      NULL,
    created_at             DATETIME(6) NOT NULL,
    modified_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (sanction_history_id),
    KEY idx_sanction_history_target_created (target_type, target_id, created_at),
    KEY idx_sanction_history_admin (processed_by_admin_id),
    KEY idx_sanction_history_report (source_report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE operation_failure_log (
    operation_failure_log_id BIGINT        NOT NULL AUTO_INCREMENT,
    category                 ENUM('EXTERNAL_API','PAYMENT_WEBHOOK','REFUND','SCHEDULER')   NOT NULL,
    operation                VARCHAR(200)  NOT NULL,
    ref_type                 VARCHAR(50)   NULL,
    ref_id                   VARCHAR(255)  NULL,
    error_code               VARCHAR(100)  NULL,
    error_message            VARCHAR(1000) NULL,
    payload                  VARCHAR(4000) NULL,
    created_at               DATETIME(6)   NOT NULL,
    modified_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (operation_failure_log_id),
    KEY idx_ofl_category_created (category, created_at),
    KEY idx_ofl_ref (ref_type, ref_id),
    KEY idx_ofl_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- AI Manager
-- =========================================================

CREATE TABLE ai_generated_message (
    ai_generated_message_id BIGINT       NOT NULL AUTO_INCREMENT,
    store_id                BIGINT       NOT NULL,
    owner_account_id        BIGINT       NOT NULL,
    type                    ENUM('COMPLAINT_REPLY','CUSTOMER_CARE','EVENT_MARKETING','INQUIRY_REPLY','LOCAL_MATCH','NOTICE','REVIEW_REPLY','RISK_GUIDE','SAVING_PLAN')  NOT NULL,
    target_type             VARCHAR(30)  NULL,
    target_id               BIGINT       NULL,
    title                   VARCHAR(200) NULL,
    content                 TEXT         NOT NULL,
    original_content        TEXT         NOT NULL,
    status                  ENUM('CANCELLED','DRAFT','FAILED','REVIEWED','SCHEDULED','SENT')  NOT NULL,
    channel                 ENUM('APP_PUSH','KAKAO_ALERT','SNS_CARD','STORE_NOTICE')  NULL,
    scheduled_at            DATETIME(6)  NULL,
    sent_at                 DATETIME(6)  NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    retry_count             INT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NOT NULL,
    modified_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (ai_generated_message_id),
    CONSTRAINT fk_ai_generated_message_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_ai_generated_message_owner
        FOREIGN KEY (owner_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_action_log (
    ai_action_log_id BIGINT       NOT NULL AUTO_INCREMENT,
    store_id         BIGINT       NOT NULL,
    owner_account_id BIGINT       NOT NULL,
    action_type      ENUM('CHATBOT_ANSWERED','DRAFT_CREATED','EXPOSURE_STARTED','EXPOSURE_STOPPED','MESSAGE_CANCELLED','MESSAGE_SCHEDULED','MESSAGE_SENT','NOTICE_PUBLISHED','OWNER_METRIC_INPUT','SAVING_PLAN_SAVED')  NOT NULL,
    target_type      VARCHAR(30)  NULL,
    target_id        BIGINT       NULL,
    description      VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    modified_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (ai_action_log_id),
    CONSTRAINT fk_ai_action_log_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_ai_action_log_owner
        FOREIGN KEY (owner_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_ad_click_log (
    ai_ad_click_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id           BIGINT      NOT NULL,
    exposure_status_id BIGINT      NOT NULL,
    viewer_account_id  BIGINT      NULL,
    clicked_at         DATETIME(6) NOT NULL,
    request_id         VARCHAR(64) NULL,
    created_at         DATETIME(6) NOT NULL,
    modified_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_ad_click_log_id),
    KEY idx_ai_ad_click_store (store_id, clicked_at),
    CONSTRAINT fk_ai_ad_click_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_ad_exposure_log (
    ai_ad_exposure_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id              BIGINT      NOT NULL,
    exposure_status_id    BIGINT      NOT NULL,
    viewer_account_id     BIGINT      NULL,
    exposed_at            DATETIME(6) NOT NULL,
    source                VARCHAR(30) NULL,
    request_id            VARCHAR(64) NULL,
    created_at            DATETIME(6) NOT NULL,
    modified_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_ad_exposure_log_id),
    KEY idx_ai_ad_exposure_store (store_id, exposed_at),
    CONSTRAINT fk_ai_ad_exposure_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_chat_message (
    ai_chat_message_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id           BIGINT      NOT NULL,
    owner_account_id   BIGINT      NOT NULL,
    role               ENUM('ASSISTANT','USER') NOT NULL,
    content            TEXT        NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    modified_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_chat_message_id),
    CONSTRAINT fk_ai_chat_message_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_ai_chat_message_owner
        FOREIGN KEY (owner_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_conversion_event (
    ai_conversion_event_id  BIGINT      NOT NULL AUTO_INCREMENT,
    ai_generated_message_id BIGINT      NOT NULL,
    store_id                BIGINT      NOT NULL,
    account_id              BIGINT      NOT NULL,
    conversion_type         ENUM('ORDER','RESERVATION') NOT NULL,
    order_id                BIGINT      NULL,
    reservation_id          BIGINT      NULL,
    converted_at            DATETIME(6) NOT NULL,
    attribution_window_days INT         NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    modified_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_conversion_event_id),
    CONSTRAINT uk_ai_conversion_message_account
        UNIQUE (ai_generated_message_id, account_id),
    KEY idx_ai_conversion_store (store_id, converted_at),
    CONSTRAINT fk_ai_conversion_message
        FOREIGN KEY (ai_generated_message_id) REFERENCES ai_generated_message(ai_generated_message_id),
    CONSTRAINT fk_ai_conversion_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_exposure_status (
    ai_exposure_status_id BIGINT      NOT NULL AUTO_INCREMENT,
    version               BIGINT      NOT NULL DEFAULT 0,
    store_id              BIGINT      NOT NULL,
    active                BIT(1)      NOT NULL,
    started_at            DATETIME(6) NULL,
    stopped_at            DATETIME(6) NULL,
    target_count          INT         NOT NULL,
    radius_km             DOUBLE      NOT NULL,
    interest              VARCHAR(50) NULL,
    customer_type         ENUM('ALL','NEW','REGULAR') NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    modified_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_exposure_status_id),
    CONSTRAINT uk_ai_exposure_store UNIQUE (store_id),
    CONSTRAINT fk_ai_exposure_status_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_message_delivery (
    ai_message_delivery_id BIGINT       NOT NULL AUTO_INCREMENT,
    ai_generated_message_id BIGINT      NOT NULL,
    store_id               BIGINT       NOT NULL,
    target_account_id      BIGINT       NULL,
    channel                ENUM('APP_PUSH','KAKAO_ALERT','SNS_CARD','STORE_NOTICE')  NOT NULL,
    status                 ENUM('FAILED','NO_TARGET','SENT','SKIPPED_DND','SKIPPED_NO_CONSENT','SKIPPED_NO_TOKEN')  NOT NULL,
    sent_at                DATETIME(6)  NULL,
    failed_reason          VARCHAR(200) NULL,
    provider_message_id    VARCHAR(200) NULL,
    created_at             DATETIME(6)  NOT NULL,
    modified_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (ai_message_delivery_id),
    KEY idx_ai_delivery_message (ai_generated_message_id),
    KEY idx_ai_delivery_target_store (target_account_id, store_id, sent_at),
    CONSTRAINT fk_ai_message_delivery_message
        FOREIGN KEY (ai_generated_message_id) REFERENCES ai_generated_message(ai_generated_message_id),
    CONSTRAINT fk_ai_message_delivery_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_owner_metric_input (
    ai_owner_metric_input_id BIGINT        NOT NULL AUTO_INCREMENT,
    store_id                 BIGINT        NOT NULL,
    metric_type              ENUM('AIR_CONDITIONER_COUNT','BUSINESS_DAYS_PER_MONTH','EQUIPMENT_COUNT','GAS_EQUIPMENT_COUNT','HEATING_EQUIPMENT_COUNT','MONTHLY_GAS_BILL','MONTHLY_GAS_USAGE_M3','MONTHLY_POWER_BILL','MONTHLY_POWER_KWH','OPEN_HOURS_PER_DAY','REFRIGERATOR_COUNT')   NOT NULL,
    metric_value             DECIMAL(12,2) NOT NULL,
    metric_year_month        VARCHAR(7)    NOT NULL,
    created_at               DATETIME(6)   NOT NULL,
    modified_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (ai_owner_metric_input_id),
    CONSTRAINT uk_ai_metric_store_type_month
        UNIQUE (store_id, metric_type, metric_year_month),
    CONSTRAINT fk_ai_owner_metric_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_plan_payment (
    ai_plan_payment_id  BIGINT        NOT NULL AUTO_INCREMENT,
    store_id            BIGINT        NOT NULL,
    plan_type           ENUM('BASIC','FREE','PRO')   NOT NULL,
    amount              DECIMAL(10,2) NOT NULL,
    portone_payment_id  VARCHAR(100)  NOT NULL,
    status              ENUM('CANCELLED','FAILED','PAID','PENDING')   NOT NULL,
    paid_at             DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    modified_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (ai_plan_payment_id),
    CONSTRAINT uk_ai_plan_payment_portone_id UNIQUE (portone_payment_id),
    CONSTRAINT fk_ai_plan_payment_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_plan_subscription (
    ai_plan_subscription_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id                BIGINT      NOT NULL,
    plan_type               ENUM('BASIC','FREE','PRO') NOT NULL,
    started_at              DATETIME(6) NOT NULL,
    expired_at              DATETIME(6) NULL,
    active                  BIT(1)      NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    modified_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_plan_subscription_id),
    CONSTRAINT fk_ai_plan_subscription_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_saving_plan (
    ai_saving_plan_id               BIGINT        NOT NULL AUTO_INCREMENT,
    store_id                        BIGINT        NOT NULL,
    title                           VARCHAR(100)  NOT NULL,
    expected_monthly_saving_amount  DECIMAL(12,2) NULL,
    status                          ENUM('DRAFT','SAVED')   NOT NULL,
    created_at                      DATETIME(6)   NOT NULL,
    modified_at                     DATETIME(6)   NOT NULL,
    PRIMARY KEY (ai_saving_plan_id),
    CONSTRAINT fk_ai_saving_plan_store
        FOREIGN KEY (store_id) REFERENCES store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_saving_plan_item (
    ai_saving_plan_item_id          BIGINT        NOT NULL AUTO_INCREMENT,
    ai_saving_plan_id               BIGINT        NOT NULL,
    title                           VARCHAR(100)  NOT NULL,
    difficulty                      VARCHAR(20)   NOT NULL,
    start_timing                    VARCHAR(30)   NOT NULL,
    expected_monthly_saving_amount  DECIMAL(12,2) NULL,
    selected                        BIT(1)        NOT NULL,
    created_at                      DATETIME(6)   NOT NULL,
    modified_at                     DATETIME(6)   NOT NULL,
    PRIMARY KEY (ai_saving_plan_item_id),
    CONSTRAINT fk_ai_saving_plan_item_plan
        FOREIGN KEY (ai_saving_plan_id) REFERENCES ai_saving_plan(ai_saving_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_usage_log (
    ai_usage_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    store_id        BIGINT      NOT NULL,
    owner_account_id BIGINT     NOT NULL,
    usage_type      ENUM('CHATBOT_GENERATION','COMPLAINT_DRAFT','CUSTOMER_CARE_DRAFT','INQUIRY_REPLY_DRAFT','MARKETING_DRAFT','NOTICE_DRAFT','REVIEW_REPLY_DRAFT') NOT NULL,
    usage_year_month VARCHAR(7) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    modified_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_usage_log_id),
    KEY idx_ai_usage_store_month (store_id, usage_year_month),
    CONSTRAINT fk_ai_usage_log_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_ai_usage_log_owner
        FOREIGN KEY (owner_account_id) REFERENCES account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- External imported/public data
-- =========================================================

CREATE TABLE external_building_energy_stat (
    external_building_energy_stat_id BIGINT        NOT NULL AUTO_INCREMENT,
    source_name                      VARCHAR(100)  NOT NULL,
    source_id                        VARCHAR(20)   NOT NULL,
    source_period                    VARCHAR(10)   NOT NULL,
    sido                             VARCHAR(30)   NULL,
    sigungu                          VARCHAR(30)   NULL,
    legal_dong_code                  VARCHAR(20)   NULL,
    legal_dong_name                  VARCHAR(50)   NULL,
    usage_kwh                        DECIMAL(18,2) NULL,
    building_count                   BIGINT        NULL,
    building_type                    VARCHAR(50)   NULL,
    source_updated_at                DATE          NULL,
    imported_at                      DATETIME(6)   NOT NULL,
    created_at                       DATETIME(6)   NOT NULL,
    modified_at                      DATETIME(6)   NOT NULL,
    PRIMARY KEY (external_building_energy_stat_id),
    KEY idx_ext_building_period_region (source_period, sigungu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE external_data_import_history (
    external_data_import_history_id BIGINT        NOT NULL AUTO_INCREMENT,
    data_name                       VARCHAR(100)  NOT NULL,
    source_id                       VARCHAR(20)   NOT NULL,
    file_name                       VARCHAR(300)  NOT NULL,
    source_updated_at               DATE          NULL,
    imported_at                     DATETIME(6)   NOT NULL,
    status                          ENUM('FAILED','PARTIAL_SUCCESS','SUCCESS')   NOT NULL,
    total_rows                      INT           NOT NULL,
    success_rows                    INT           NOT NULL,
    failed_rows                     INT           NOT NULL,
    failure_reason                  VARCHAR(500)  NULL,
    created_at                      DATETIME(6)   NOT NULL,
    modified_at                     DATETIME(6)   NOT NULL,
    PRIMARY KEY (external_data_import_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE external_energy_usage_stat (
    external_energy_usage_stat_id BIGINT        NOT NULL AUTO_INCREMENT,
    source_name                   VARCHAR(100)  NOT NULL,
    source_id                     VARCHAR(20)   NOT NULL,
    source_period                 VARCHAR(10)   NOT NULL,
    sido                          VARCHAR(30)   NULL,
    sigungu                       VARCHAR(30)   NULL,
    legal_dong_code               VARCHAR(20)   NULL,
    legal_dong_name               VARCHAR(50)   NULL,
    usage_type                    VARCHAR(50)   NULL,
    usage_kwh                     DECIMAL(18,2) NULL,
    charge_amount                 DECIMAL(18,2) NULL,
    customer_count                BIGINT        NULL,
    average_unit_price            DECIMAL(12,2) NULL,
    source_updated_at             DATE          NULL,
    imported_at                   DATETIME(6)   NOT NULL,
    created_at                    DATETIME(6)   NOT NULL,
    modified_at                   DATETIME(6)   NOT NULL,
    PRIMARY KEY (external_energy_usage_stat_id),
    KEY idx_ext_energy_period_region (source_period, sigungu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
