// src/components/owner/ai/AiRiskBadge.jsx
import React from 'react';
import styled from 'styled-components';
import { AI_RISK_LEVEL_MAP, AI_RISK_LEVEL_COLOR } from '../../../constants/aiConstants';

const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;
  background: ${(props) => props.$bg};
  color: ${(props) => props.$color};

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }
`;

function AiRiskBadge({ level }) {
  const palette = AI_RISK_LEVEL_COLOR[level] ?? {
    bg: '#f1f3f5',
    color: '#8e94a0',
  };

  return (
    <Badge
      $bg={palette.bg}
      $color={palette.color}
    >
      {AI_RISK_LEVEL_MAP[level] ?? '측정 전'}
    </Badge>
  );
}

export default AiRiskBadge;
