ALTER TABLE weekly_settlement
    ADD COLUMN claimed_by BIGINT NULL AFTER claim_expires_at,
    ADD CONSTRAINT fk_weekly_settlement_claimed_by
        FOREIGN KEY (claimed_by) REFERENCES account(account_id);
