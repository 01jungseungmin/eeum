ALTER TABLE ai_plan_payment_cancellation_operation
    ADD COLUMN last_checked_at DATETIME(6) NULL AFTER resolved_at,
    ADD INDEX idx_ai_plan_payment_cancel_reconcile (status, last_checked_at, ai_plan_payment_cancellation_operation_id);
