-- 주문별 수익 원장: AI 플랜 결제와 섞이지 않도록 Order를 기준으로 한 건만 생성한다.
CREATE TABLE owner_revenue (
    owner_revenue_id       BIGINT          NOT NULL AUTO_INCREMENT,
    order_id               BIGINT          NOT NULL,
    payment_id             BIGINT          NOT NULL,
    store_id               BIGINT          NOT NULL,
    payment_amount         DECIMAL(10,2)   NOT NULL,
    pg_fee_amount          DECIMAL(10,2)   NOT NULL,
    platform_fee_amount    DECIMAL(10,2)   NOT NULL,
    payout_amount          DECIMAL(10,2)   NOT NULL,
    status                 ENUM('ACCRUED','SETTLEMENT_PENDING','SETTLED','CANCELLED') NOT NULL,
    -- 결제 완료 시 원장을 먼저 만들고, 주문 완료 시 completed_at + 7일로 채운다.
    settleable_at          DATETIME(6)     NULL,
    cancelled_at           DATETIME(6)     NULL,
    cancel_reason          VARCHAR(500)    NULL,
    created_at             DATETIME(6)     NOT NULL,
    modified_at            DATETIME(6)     NOT NULL,
    PRIMARY KEY (owner_revenue_id),
    CONSTRAINT uk_owner_revenue_order UNIQUE (order_id),
    CONSTRAINT uk_owner_revenue_payment UNIQUE (payment_id),
    CONSTRAINT fk_owner_revenue_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_owner_revenue_payment
        FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    CONSTRAINT fk_owner_revenue_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    INDEX idx_owner_revenue_settleable (status, settleable_at, owner_revenue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 상점별 주간 정산. 기간은 [period_start_at, period_end_at) 반열린 구간으로 관리한다.
CREATE TABLE weekly_settlement (
    weekly_settlement_id       BIGINT          NOT NULL AUTO_INCREMENT,
    store_id                   BIGINT          NOT NULL,
    period_start_at            DATETIME(6)     NOT NULL,
    period_end_at              DATETIME(6)     NOT NULL,
    payment_amount             DECIMAL(10,2)   NOT NULL,
    pg_fee_amount              DECIMAL(10,2)   NOT NULL,
    platform_fee_amount        DECIMAL(10,2)   NOT NULL,
    payout_amount              DECIMAL(10,2)   NOT NULL,
    status                     ENUM('PAYOUT_PENDING','PAYOUT_IN_PROGRESS','COMPLETED','FAILED','MANUAL_REVIEW_REQUIRED') NOT NULL,
    payout_gateway             VARCHAR(30)     NULL,
    payout_external_id         VARCHAR(100)    NULL,
    payout_idempotency_key     VARCHAR(100)    NOT NULL,
    payout_result_status       VARCHAR(50)     NULL,
    payout_requested_at        DATETIME(6)     NULL,
    payout_completed_at        DATETIME(6)     NULL,
    failure_code               VARCHAR(100)    NULL,
    failure_reason             VARCHAR(500)    NULL,
    claim_token                VARCHAR(100)    NULL,
    claim_expires_at           DATETIME(6)     NULL,
    manual_completed_by        BIGINT          NULL,
    manual_payout_reference    VARCHAR(100)    NULL,
    version                    BIGINT          NOT NULL DEFAULT 0,
    created_at                 DATETIME(6)     NOT NULL,
    modified_at                DATETIME(6)     NOT NULL,
    PRIMARY KEY (weekly_settlement_id),
    CONSTRAINT uk_weekly_settlement_period UNIQUE (store_id, period_start_at, period_end_at),
    CONSTRAINT uk_weekly_settlement_idempotency_key UNIQUE (payout_idempotency_key),
    CONSTRAINT fk_weekly_settlement_store
        FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_weekly_settlement_manual_completed_by
        FOREIGN KEY (manual_completed_by) REFERENCES account(account_id),
    INDEX idx_weekly_settlement_store_period (store_id, period_end_at, weekly_settlement_id),
    INDEX idx_weekly_settlement_claim (status, claim_expires_at, weekly_settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 정산을 확정한 시점의 원장 금액 스냅샷. 한 원장은 하나의 주간 정산에만 포함될 수 있다.
CREATE TABLE weekly_settlement_item (
    weekly_settlement_item_id  BIGINT          NOT NULL AUTO_INCREMENT,
    weekly_settlement_id       BIGINT          NOT NULL,
    owner_revenue_id           BIGINT          NOT NULL,
    payment_amount             DECIMAL(10,2)   NOT NULL,
    pg_fee_amount              DECIMAL(10,2)   NOT NULL,
    platform_fee_amount        DECIMAL(10,2)   NOT NULL,
    payout_amount              DECIMAL(10,2)   NOT NULL,
    created_at                 DATETIME(6)     NOT NULL,
    modified_at                DATETIME(6)     NOT NULL,
    PRIMARY KEY (weekly_settlement_item_id),
    CONSTRAINT uk_weekly_settlement_item_revenue UNIQUE (owner_revenue_id),
    CONSTRAINT fk_weekly_settlement_item_settlement
        FOREIGN KEY (weekly_settlement_id) REFERENCES weekly_settlement(weekly_settlement_id),
    CONSTRAINT fk_weekly_settlement_item_revenue
        FOREIGN KEY (owner_revenue_id) REFERENCES owner_revenue(owner_revenue_id),
    INDEX idx_weekly_settlement_item_settlement (weekly_settlement_id, weekly_settlement_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
