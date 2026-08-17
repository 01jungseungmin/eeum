import React from 'react';
import styled from 'styled-components';

// ---------------- Styled Components ----------------
const CardsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const Card = styled.div`
  background-color: ${(props) => props.$bgColor || '#ffffff'};
  border: 1px solid ${(props) => props.$borderColor || '#e5e7eb'};
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const CardTitle = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: #374151;
`;

const CardValue = styled.div`
  font-size: 32px;
  font-weight: 800;
  color: #111827;
  line-height: 1;
`;

const CardBadge = styled.span`
  align-self: flex-start;
  font-size: 13px;
  font-weight: 700;
  color: ${(props) => props.$color || '#6b7280'};
`;

// ---------------- Component ----------------
const StatCards = () => {
  const statData = [
    {
      id: 1,
      title: '오늘 게시글',
      value: '284',
      badgeText: '+12.4%',
      bgColor: '#F0FDF4',
      borderColor: '#DCFCE7',
      badgeColor: '#16A34A',
    },
    {
      id: 2,
      title: '신고됨',
      value: '7',
      badgeText: '처리 필요',
      bgColor: '#FEF2F2',
      borderColor: '#FEE2E2',
      badgeColor: '#DC2626',
    },
    {
      id: 3,
      title: '자동 숨김',
      value: '23',
      badgeText: '오늘',
      bgColor: '#FFFBEB',
      borderColor: '#FEF3C7',
      badgeColor: '#D97706',
    },
    {
      id: 4,
      title: '총 댓글',
      value: '1,892',
      badgeText: '오늘',
      bgColor: '#EFF6FF',
      borderColor: '#DBEAFE',
      badgeColor: '#2563EB',
    },
  ];

  return (
    <CardsGrid>
      {statData.map((card) => (
        <Card
          key={card.id}
          $bgColor={card.bgColor}
          $borderColor={card.borderColor}
        >
          <CardTitle>{card.title}</CardTitle>
          <CardValue>{card.value}</CardValue>
          <CardBadge $color={card.badgeColor}>{card.badgeText}</CardBadge>
        </Card>
      ))}
    </CardsGrid>
  );
};

export default StatCards;
