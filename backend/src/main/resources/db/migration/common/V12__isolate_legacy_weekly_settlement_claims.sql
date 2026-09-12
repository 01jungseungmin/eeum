UPDATE weekly_settlement
SET status = 'MANUAL_REVIEW_REQUIRED',
    failure_code = 'CLAIMED_BY_MIGRATION_REQUIRED',
    failure_reason = 'claimed_by 도입 전 진행 중이던 지급 claim은 수동 확인이 필요합니다.'
WHERE status = 'PAYOUT_IN_PROGRESS'
  AND claimed_by IS NULL;
