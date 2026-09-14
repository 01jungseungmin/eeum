UPDATE owner_revenue
SET pg_fee_rate = NULL,
    platform_fee_rate = NULL;

ALTER TABLE payment
    MODIFY COLUMN cancelled_amount DECIMAL(10,2) NOT NULL DEFAULT 0
    COMMENT 'PG에서 누적 확정된 부분 취소 금액';

ALTER TABLE owner_revenue
    MODIFY COLUMN pg_fee_rate DECIMAL(10,6) NULL
    COMMENT '원장 생성 시점 PG 수수료율. NULL이면 수동 대사 필요',
    MODIFY COLUMN platform_fee_rate DECIMAL(10,6) NULL
    COMMENT '원장 생성 시점 플랫폼 수수료율. NULL이면 수동 대사 필요';
