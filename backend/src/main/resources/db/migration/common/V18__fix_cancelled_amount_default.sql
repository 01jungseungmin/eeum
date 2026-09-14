ALTER TABLE payment
    MODIFY COLUMN cancelled_amount DECIMAL(10,2) NOT NULL DEFAULT 0
    COMMENT 'PG에서 누적 확정된 부분 취소 금액';
