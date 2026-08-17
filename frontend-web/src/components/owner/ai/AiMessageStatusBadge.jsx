// src/components/owner/ai/AiMessageStatusBadge.jsx
import React from 'react';
import styled from 'styled-components';
import {
  AI_MESSAGE_STATUS_MAP,
  AI_MESSAGE_STATUS_COLOR,
} from '../../../constants/aiConstants';

const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;
  background: ${(props) => props.$bg};
  color: ${(props) => props.$color};
  white-space: nowrap;
`;

function AiMessageStatusBadge({ status }) {
  const palette = AI_MESSAGE_STATUS_COLOR[status] ?? {
    bg: '#f1f3f5',
    color: '#8e94a0',
  };

  return (
    <Badge
      $bg={palette.bg}
      $color={palette.color}
    >
      {AI_MESSAGE_STATUS_MAP[status] ?? status}
    </Badge>
  );
}

export default AiMessageStatusBadge;
