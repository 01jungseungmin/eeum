import React from 'react';
import styled from 'styled-components';

const CardsWrapper = styled.div`
  display: flex;
  gap: 40px;
  margin-bottom: 24px;
`;

const CardContainer = styled.div`
  flex: 1;
  background: #fff;
  border: 1px solid ${(props) => (props.$active ? '#111' : '#eee')}; /* $active 사용 */
  border-radius: 16px;
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.2s ease-in-out;
  box-shadow: ${(props) =>
    props.$active ? '0 4px 12px rgba(0, 0, 0, 0.05)' : 'none'};

  &:hover {
    background: #fafafa;
    border-color: ${(props) => (props.$active ? '#111' : '#ccc')};
  }
`;

const CardTitle = styled.div`
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;

  /* 상태별 텍스트 색상 정의 */
  color: ${(props) => {
    if (props.type === 'total') return '#495057';
    if (props.type === 'pending') return '#ff6b6b'; // 미답변 (빨간색 계열)
    if (props.type === 'completed') return '#0ca678'; // 답변완료 (초록색 계열)
    return '#495057';
  }};
`;

const CardValue = styled.div`
  font-size: 28px;
  font-weight: 700;
  color: #111;
`;

const StatusDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: ${(props) => {
    if (props.type === 'total') return '#adb5bd';
    if (props.type === 'pending') return '#ff6b6b';
    if (props.type === 'completed') return '#0ca678';
    return '#adb5bd';
  }};
`;

export default function SummaryCards({
  total,
  pending,
  completed,
  currentStatus,
  setStatusFilter,
}) {
  return (
    <CardsWrapper>
      {/* 전체 문의 카드 */}
      <CardContainer
        $active={currentStatus === '전체'}
        onClick={() => setStatusFilter('전체')}
      >
        <CardTitle type="total">
          <StatusDot type="total" />
          전체 문의
        </CardTitle>
        <CardValue>{total}</CardValue>
      </CardContainer>

      {/* 미답변 카드 */}
      <CardContainer
        $active={currentStatus === '미답변'}
        onClick={() => setStatusFilter('미답변')}
      >
        <CardTitle type="pending">
          <StatusDot type="pending" />
          미답변
        </CardTitle>
        <CardValue>{pending}</CardValue>
      </CardContainer>

      {/* 답변완료 카드 */}
      <CardContainer
        $active={currentStatus === '답변완료'}
        onClick={() => setStatusFilter('답변완료')}
      >
        <CardTitle type="completed">
          <StatusDot type="completed" />
          답변완료
        </CardTitle>
        <CardValue>{completed}</CardValue>
      </CardContainer>
    </CardsWrapper>
  );
}
