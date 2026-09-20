-- payout_gateway는 VARCHAR(30)이라 DB가 값을 검증하지 못했다. 앱 밖에서 수동 INSERT된
-- 'DEMO_BANK'가 들어가, PayoutGatewayType에 없는 값을 역매핑하다 정산 조회 전체
-- (관리자 목록 · 사장 주간정산)가 500으로 죽었다. 행이 2건뿐이라 어느 페이지를 열어도
-- 항상 그 행이 포함돼 100% 재현됐다.
--
-- 같은 테이블의 status는 처음부터 MySQL ENUM이라 이 문제가 없었다. payout_gateway도
-- 같은 방식으로 맞춰, 앱을 거치지 않는 수동 입력까지 DB가 막게 한다.
-- 게이트웨이가 늘어나면 이 ENUM에 값을 추가하는 마이그레이션이 함께 필요하다.

UPDATE weekly_settlement
SET payout_gateway = 'MANUAL'
WHERE payout_gateway IS NOT NULL
  AND payout_gateway <> 'MANUAL';

ALTER TABLE weekly_settlement
    MODIFY COLUMN payout_gateway ENUM('MANUAL') NULL
    COMMENT '지급 게이트웨이. PayoutGatewayType과 값이 일치해야 한다';
