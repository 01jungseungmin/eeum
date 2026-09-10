ALTER TABLE owner_revenue
    ADD CONSTRAINT chk_owner_revenue_amounts CHECK (
        payment_amount >= 0 AND pg_fee_amount >= 0 AND platform_fee_amount >= 0
        AND payout_amount >= 0
        AND payout_amount = payment_amount - pg_fee_amount - platform_fee_amount
    );

ALTER TABLE weekly_settlement
    ADD CONSTRAINT chk_weekly_settlement_period CHECK (period_start_at < period_end_at),
    ADD CONSTRAINT chk_weekly_settlement_amounts CHECK (
        payment_amount >= 0 AND pg_fee_amount >= 0 AND platform_fee_amount >= 0
        AND payout_amount >= 0
        AND payout_amount = payment_amount - pg_fee_amount - platform_fee_amount
    );

ALTER TABLE weekly_settlement_item
    ADD CONSTRAINT chk_weekly_settlement_item_amounts CHECK (
        payment_amount >= 0 AND pg_fee_amount >= 0 AND platform_fee_amount >= 0
        AND payout_amount >= 0
        AND payout_amount = payment_amount - pg_fee_amount - platform_fee_amount
    );
