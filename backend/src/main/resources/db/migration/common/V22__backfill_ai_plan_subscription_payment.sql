-- V20에서 추가한 결제 연결을, 결제 완료 시각과 구독 시작 시각이 일치하는 기존 데이터에 안전하게 채운다.
-- 정확히 매칭되지 않는 레거시 행은 NULL로 보존한다. 재전송은 서비스의 시간 순서 가드가 최신 구독을 보호한다.
UPDATE ai_plan_subscription subscription
JOIN ai_plan_payment payment
  ON payment.store_id = subscription.store_id
 AND payment.plan_type = subscription.plan_type
 AND payment.status = 'PAID'
 AND payment.paid_at = subscription.started_at
SET subscription.ai_plan_payment_id = payment.ai_plan_payment_id
WHERE subscription.ai_plan_payment_id IS NULL;
