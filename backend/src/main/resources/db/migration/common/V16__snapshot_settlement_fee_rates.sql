ALTER TABLE owner_revenue
    ADD COLUMN pg_fee_rate DECIMAL(10,6) NOT NULL DEFAULT 0
    COMMENT '원장 생성 시점 PG 수수료율',
    ADD COLUMN platform_fee_rate DECIMAL(10,6) NOT NULL DEFAULT 0
    COMMENT '원장 생성 시점 플랫폼 수수료율';

UPDATE owner_revenue
SET pg_fee_rate = CASE
        WHEN payment_amount = 0 THEN 0
        ELSE ROUND(pg_fee_amount / payment_amount, 6)
    END,
    platform_fee_rate = CASE
        WHEN payment_amount = 0 THEN 0
        ELSE ROUND(platform_fee_amount / payment_amount, 6)
    END;
