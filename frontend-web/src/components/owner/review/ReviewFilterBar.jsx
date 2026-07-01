import React from 'react';
import styled from 'styled-components';

// --- Styled Components (최상단 위치) ---
const BarContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 16px 24px;
  border: 1px solid #f3f4f6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  width: 100%;
`;

const TabGroup = styled.div`
  display: flex;
  gap: 8px;
`;

// Transient prop ($active) 처리로 콘솔 경고 제거
const TabButton = styled.button`
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid ${(props) => (props.$active ? '#10b981' : '#e5e7eb')};
  background-color: ${(props) => (props.$active ? '#f0fdf4' : '#ffffff')};
  color: ${(props) => (props.$active ? '#10b981' : '#4b5563')};
  transition: all 0.2s ease;

  &:hover {
    background-color: ${(props) => (props.$active ? '#f0fdf4' : '#f9fafb')};
  }
`;

const CountBadge = styled.span`
  margin-left: 6px;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 10px;
  background: ${(props) => (props.$active ? '#10b981' : '#f3f4f6')};
  color: ${(props) => (props.$active ? '#ffffff' : '#6b7280')};
`;

const SelectBox = styled.select`
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  font-size: 13px;
  color: #374151;
  outline: none;
  background-color: #ffffff;
  cursor: pointer;

  &:focus {
    border-color: #10b981;
  }
`;

export default function ReviewFilterBar({
  currentStatus,
  onStatusChange,
  currentRating,
  onRatingChange,
  totalCount,
  unansweredCount,
  answeredCount,
}) {
  return (
    <BarContainer>
      {/* 1. 답변 상태 분기 탭 */}
      <TabGroup>
        <TabButton
          $active={currentStatus === 'ALL'}
          onClick={() => onStatusChange('ALL')}
        >
          전체 리뷰
          <CountBadge $active={currentStatus === 'ALL'}>
            {totalCount}
          </CountBadge>
        </TabButton>

        <TabButton
          $active={currentStatus === 'UNANSWERED'}
          onClick={() => onStatusChange('UNANSWERED')}
        >
          미답글 리뷰
          <CountBadge $active={currentStatus === 'UNANSWERED'}>
            {unansweredCount}
          </CountBadge>
        </TabButton>

        <TabButton
          $active={currentStatus === 'ANSWERED'}
          onClick={() => onStatusChange('ANSWERED')}
        >
          답글 완료
          <CountBadge $active={currentStatus === 'ANSWERED'}>
            {answeredCount}
          </CountBadge>
        </TabButton>
      </TabGroup>

      {/* 2. 별점 다중 선택 필터 셀렉트박스 */}
      <SelectBox
        value={currentRating}
        onChange={(e) => onRatingChange(e.target.value)}
      >
        <option value="ALL">별점 전체 보기</option>
        <option value="5">5점만 보기</option>
        <option value="4">4점만 보기</option>
        <option value="3">3점만 보기</option>
        <option value="2">2점만 보기</option>
        <option value="1">1점만 보기</option>
      </SelectBox>
    </BarContainer>
  );
}
