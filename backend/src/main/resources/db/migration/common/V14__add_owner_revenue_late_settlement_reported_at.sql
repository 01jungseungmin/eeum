ALTER TABLE owner_revenue
    ADD COLUMN late_settlement_reported_at DATETIME(6) NULL
    COMMENT '기간 밖 누락 정산 원장을 운영 수습 대상으로 기록한 시각';
