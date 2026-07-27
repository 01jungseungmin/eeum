import React from 'react';
import styled from 'styled-components';

const CardsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
`;

const Card = styled.div`
  border-radius: 12px;
  padding: 18px 20px;
  background-color: ${(props) => props.$bg || '#ffffff'};
  border: 1px solid ${(props) => props.$borderColor || '#e2e8f0'};
`;

const CardLabel = styled.div`
  font-size: 13px;
  color: ${(props) => props.$color || '#64748b'};
  margin-bottom: 8px;
  font-weight: 500;
`;

const CardValue = styled.div`
  font-size: 28px;
  font-weight: bold;
  color: ${(props) => props.$color || '#0f172a'};
`;

export default function SummaryCards() {
  return (
    <CardsGrid>
      <Card $bg="#ffffff" $borderColor="#e2e8f0">
        <CardLabel $color="#64748b">전체 신고</CardLabel>
        <CardValue>4</CardValue>
      </Card>

      <Card $bg="#fffbeb" $borderColor="#fde68a">
        <CardLabel $color="#b45309">검토중</CardLabel>
        <CardValue $color="#b45309">2</CardValue>
      </Card>

      <Card $bg="#f0fdf4" $borderColor="#bbf7d0">
        <CardLabel $color="#15803d">처리완료</CardLabel>
        <CardValue $color="#15803d">2</CardValue>
      </Card>
    </CardsGrid>
  );
}
