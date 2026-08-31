// src/components/owner/ai/AiSectionCard.jsx
import React from 'react';
import styled from 'styled-components';
import { ChevronRight } from 'lucide-react';

const CardContainer = styled.section`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #eef0f2;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 18px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const TitleGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;

  .icon-box {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: #f1f3f5;
    color: #4a5568;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: bold;
    color: #1a1f2c;
  }
  p {
    margin: 2px 0 0 0;
    font-size: 12px;
    color: #8e94a0;
  }
`;

const MoreButton = styled.button`
  display: flex;
  align-items: center;
  gap: 2px;
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  font-weight: bold;
  color: #00a651;
  cursor: pointer;

  &:hover {
    color: #008f45;
  }
`;

function AiSectionCard({ icon, title, subtitle, onMore, moreLabel = '자세히', extra, children }) {
  return (
    <CardContainer>
      <CardHeader>
        <TitleGroup>
          {icon && <div className="icon-box">{icon}</div>}
          <div>
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
        </TitleGroup>

        {extra}
        {onMore && (
          <MoreButton
            type="button"
            onClick={onMore}
          >
            {moreLabel}
            <ChevronRight size={15} />
          </MoreButton>
        )}
      </CardHeader>

      {children}
    </CardContainer>
  );
}

export default AiSectionCard;
