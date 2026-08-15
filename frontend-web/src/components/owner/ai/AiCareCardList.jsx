// src/components/owner/ai/AiCareCardList.jsx
import React from 'react';
import styled from 'styled-components';
import { ShoppingCart, UserMinus, MessageCircleQuestion } from 'lucide-react';
import { AI_CARE_TYPE_MAP } from '../../../constants/aiConstants';

const CARE_TYPE_ICON = {
  CART_INTEREST: <ShoppingCart size={18} />,
  INACTIVE_REGULAR: <UserMinus size={18} />,
  INQUIRY_HESITATION: <MessageCircleQuestion size={18} />,
};

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
`;

const CareCard = styled.button`
  text-align: left;
  background: #f8f9fa;
  border: 1px solid #eef0f2;
  border-radius: 12px;
  padding: 18px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #ffffff;
    border-color: #00a651;
  }

  .icon-box {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: #e6f7ee;
    color: #00a651;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 12px;
  }
  .title {
    font-size: 13px;
    font-weight: bold;
    color: #1a1f2c;
    margin-bottom: 8px;
  }
  .count {
    font-size: 22px;
    font-weight: bold;
    color: #1a1f2c;
  }
  .unit {
    font-size: 13px;
    font-weight: 600;
    color: #8e94a0;
    margin-left: 3px;
  }
`;

const EmptyBox = styled.div`
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: #8e94a0;
`;

function AiCareCardList({ summaries = [], onSelect }) {
  if (summaries.length === 0) {
    return <EmptyBox>아직 케어가 필요한 고객이 없어요</EmptyBox>;
  }

  return (
    <CardGrid>
      {summaries.map((summary) => (
        <CareCard
          key={summary.careType}
          type="button"
          onClick={() => onSelect?.(summary.careType)}
        >
          <div className="icon-box">{CARE_TYPE_ICON[summary.careType]}</div>
          <div className="title">
            {summary.title ?? AI_CARE_TYPE_MAP[summary.careType]}
          </div>
          <div>
            <span className="count">{summary.targetCustomerCount}</span>
            <span className="unit">명</span>
          </div>
        </CareCard>
      ))}
    </CardGrid>
  );
}

export default AiCareCardList;
