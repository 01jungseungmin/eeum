ALTER TABLE payment
    ADD COLUMN cancelled_amount DECIMAL(10,2) NOT NULL DEFAULT 0
    COMMENT 'PG에서 누적 확정된 취소 금액';

ALTER TABLE payment
    MODIFY COLUMN status ENUM('CANCELLED','FAILED','NOT_PAID','PAID','PARTIALLY_REFUNDED','PENDING','REFUNDED') NOT NULL;

ALTER TABLE payment_cancellation_operation
    ADD COLUMN pg_cancelled_amount DECIMAL(10,2) NULL
    COMMENT 'PG에서 누적 확정된 부분 취소 금액';
