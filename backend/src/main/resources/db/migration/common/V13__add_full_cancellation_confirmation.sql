ALTER TABLE payment_cancellation_operation
    ADD COLUMN full_cancellation_confirmed BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT 'PG 취소 금액이 요청 전액과 일치함이 확인된 경우';
