CREATE TABLE payment_cancellation_operation (
    payment_cancellation_operation_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    trigger_type ENUM('CUSTOMER_CANCEL','OWNER_REFUND_APPROVAL','OWNER_ORDER_REJECT','PORTONE_WEBHOOK') NOT NULL,
    status ENUM('PENDING','PG_CANCEL_REQUESTED','PG_CANCELLED','COMPLETED','MANUAL_REVIEW_REQUIRED') NOT NULL,
    reason VARCHAR(500) NULL,
    requested_amount DECIMAL(10,2) NOT NULL,
    pg_cancellation_id VARCHAR(100) NULL,
    pg_status VARCHAR(30) NULL,
    failure_code VARCHAR(100) NULL,
    failure_reason VARCHAR(1000) NULL,
    requested_at DATETIME(6) NULL,
    pg_cancelled_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_cancellation_operation_id),
    CONSTRAINT uk_payment_cancellation_operation_order UNIQUE (order_id),
    CONSTRAINT fk_payment_cancellation_operation_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_payment_cancellation_operation_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    INDEX idx_payment_cancellation_operation_status (status, created_at)
    ,CONSTRAINT chk_payment_cancellation_operation_requested_amount CHECK (requested_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
