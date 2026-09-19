ALTER TABLE ai_plan_subscription
    ADD COLUMN ai_plan_payment_id BIGINT NULL,
    ADD CONSTRAINT uk_ai_plan_subscription_payment UNIQUE (ai_plan_payment_id),
    ADD CONSTRAINT fk_ai_plan_subscription_payment
        FOREIGN KEY (ai_plan_payment_id) REFERENCES ai_plan_payment(ai_plan_payment_id);
