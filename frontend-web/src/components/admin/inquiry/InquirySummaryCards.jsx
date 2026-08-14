import React from 'react';
import styled from 'styled-components';

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 16px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(4, 1fr);
  }
`;

const SummaryCard = styled.div`
  background-color: ${(props) => props.$bg};
  border: 1px solid ${(props) => props.$borderColor};
  border-radius: 16px;
  padding: 20px;
`;

const CardLabel = styled.div`
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
`;

const CardValue = styled.div`
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 4px;
`;

const CardSubText = styled.div`
  font-size: 12px;
  font-weight: 500;
  color: ${(props) => props.$color};
`;

export default function InquirySummaryCards({ summaryData }) {
  const data = summaryData || {
    waiting: 6,
    waitingNew: 2,
    processing: 3,
    processingAvgTime: '3.2h',
    completedThisWeek: 47,
    satisfaction: 4.7,
  };

  return (
    <SummaryGrid>
      <SummaryCard
        $bg="#FEFCE8"
        $borderColor="#FEF08A"
      >
        <CardLabel>처리 대기</CardLabel>
        <CardValue>{data.waiting}</CardValue>
        <CardSubText $color="#D97706">신규 {data.waitingNew}건</CardSubText>
      </SummaryCard>

      <SummaryCard
        $bg="#EFF6FF"
        $borderColor="#BFDBFE"
      >
        <CardLabel>처리 중</CardLabel>
        <CardValue>{data.processing}</CardValue>
        <CardSubText $color="#2563EB">
          평균 {data.processingAvgTime}
        </CardSubText>
      </SummaryCard>

      <SummaryCard
        $bg="#F0FDF4"
        $borderColor="#BBF7D0"
      >
        <CardLabel>이번 주 완료</CardLabel>
        <CardValue>{data.completedThisWeek}</CardValue>
        <CardSubText $color="#16A34A">완료됨</CardSubText>
      </SummaryCard>

      <SummaryCard
        $bg="#FAF5FF"
        $borderColor="#E9D5FF"
      >
        <CardLabel>고객 만족도</CardLabel>
        <CardValue>{data.satisfaction}</CardValue>
        <CardSubText $color="#9333EA">/5.0</CardSubText>
      </SummaryCard>
    </SummaryGrid>
  );
}
