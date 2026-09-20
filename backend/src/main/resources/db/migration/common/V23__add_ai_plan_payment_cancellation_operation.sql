CREATE TABLE ai_plan_payment_cancellation_operation (
    ai_plan_payment_cancellation_operation_id BIGINT NOT NULL AUTO_INCREMENT,
    ai_plan_payment_id BIGINT NOT NULL,
    requested_amount DECIMAL(10,2) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pg_cancellation_id VARCHAR(100) NULL,
    pg_cancelled_amount DECIMAL(10,2) NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    modified_at DATETIME NOT NULL,
    PRIMARY KEY (ai_plan_payment_cancellation_operation_id),
    CONSTRAINT uk_ai_plan_payment_cancel_payment UNIQUE (ai_plan_payment_id),
    CONSTRAINT uk_ai_plan_payment_cancel_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ai_plan_payment_cancel_payment
        FOREIGN KEY (ai_plan_payment_id) REFERENCES ai_plan_payment(ai_plan_payment_id),
    INDEX idx_ai_plan_payment_cancel_status (status, modified_at)
);
