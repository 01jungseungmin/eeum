import styled from 'styled-components';
import { Clock, ChevronRight } from 'lucide-react';
import { WEEKLY_SETTLEMENT_STATUS_LABEL } from '../../../constants/settlementConstants';

const Banner = styled.button`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 16px 20px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;

  &:hover {
    background: #fef3c7;
  }
`;

const Left = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const IconCircle = styled.div`
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fde68a;
  color: #92400e;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const Text = styled.div`
  .title {
    font-size: 14px;
    font-weight: 700;
    color: #92400e;
    margin-bottom: 2px;
  }
  .desc {
    font-size: 13px;
    color: #b45309;
  }
`;

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString('ko-KR', {
        month: '2-digit',
        day: '2-digit',
      })
    : '-';

function SettlementPendingBanner({ settlement, onClick }) {
  if (!settlement) return null;

  const amountText = Math.round(
    Number(settlement.payoutAmount || 0) / 10000,
  ).toLocaleString();
  const statusLabel =
    WEEKLY_SETTLEMENT_STATUS_LABEL[settlement.status] || settlement.status;

  return (
    <Banner onClick={onClick}>
      <Left>
        <IconCircle>
          <Clock size={16} />
        </IconCircle>
        <Text>
          <div className="title">
            정산 {statusLabel}: {amountText}만원
          </div>
          <div className="desc">
            {formatDate(settlement.periodStartAt)} ~{' '}
            {formatDate(settlement.periodEndAt)} 기간 매출이 정산 처리를
            기다리고 있어요.
          </div>
        </Text>
      </Left>
      <ChevronRight
        size={18}
        color="#b45309"
      />
    </Banner>
  );
}

export default SettlementPendingBanner;
