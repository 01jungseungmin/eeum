UPDATE owner_revenue
SET pg_fee_rate = NULL,
    platform_fee_rate = NULL;

ALTER TABLE owner_revenue
    MODIFY COLUMN pg_fee_rate DECIMAL(10,6) NULL
    COMMENT '원장 생성 시점 PG 수수료율. NULL이면 수동 대사 필요',
    MODIFY COLUMN platform_fee_rate DECIMAL(10,6) NULL
    COMMENT '원장 생성 시점 플랫폼 수수료율. NULL이면 수동 대사 필요';
