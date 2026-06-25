import React from 'react';
import styled from 'styled-components';

const Card = styled.div`
  flex: 1;
  background: #fff;
  border: 1px solid ${(props) => (props.$isActive ? '#10b981' : '#e5e7eb')};
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 94px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const CardTitle = styled.span`
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
`;

const Badge = styled.span`
  background-color: #fff5f5;
  color: #e03131;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 9999px;
`;

const Count = styled.span`
  font-size: 24px;
  font-weight: 700;
  color: ${(props) => (props.$isActive ? '#10b981' : '#1f2937')};
  margin-top: 8px;
`;

function OrderSummaryCard({ title, count, badge, $isActive }) {
  return (
    <Card $isActive={$isActive}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {badge && <Badge>{badge}</Badge>}
      </CardHeader>
      <Count $isActive={$isActive}>{count}</Count>
    </Card>
  );
}

export default OrderSummaryCard;
