import React from 'react';
import styled from 'styled-components';

const Card = styled.div`
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e9ecef;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const Title = styled.span`
  font-size: 13px;
  color: #868e96;
  font-weight: 500;
`;

const Count = styled.span`
  font-size: 28px;
  font-weight: 700;
  color: ${(props) => props.color || '#1a1a1a'};
`;

export default function SummaryCard({ title, count, color }) {
  return (
    <Card>
      <Title>{title}</Title>
      <Count color={color}>{count}</Count>
    </Card>
  );
}
