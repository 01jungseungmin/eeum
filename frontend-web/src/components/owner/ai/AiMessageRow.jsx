// src/components/owner/ai/AiMessageRow.jsx
import React from 'react';
import styled from 'styled-components';
import { Clock, Send } from 'lucide-react';

import AiMessageStatusBadge from './AiMessageStatusBadge';
import {
  AI_MESSAGE_TYPE_MAP,
  AI_CHANNEL_MAP,
} from '../../../constants/aiConstants';

const RowContainer = styled.button`
  width: 100%;
  text-align: left;
  background: #ffffff;
  border: 1px solid #eef0f2;
  border-radius: 12px;
  padding: 18px 20px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  gap: 10px;

  &:hover {
    border-color: #00a651;
  }
`;

const TopRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  .title {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: bold;
    color: #1a1f2c;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .type {
    font-size: 12px;
    font-weight: 600;
    color: #4a5568;
    background: #f1f3f5;
    border-radius: 20px;
    padding: 4px 10px;
    white-space: nowrap;
  }
`;

const Preview = styled.p`
  margin: 0;
  font-size: 13px;
  color: #8e94a0;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #8e94a0;

  .meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
`;

const formatDateTime = (value) => {
  if (!value) return null;

  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
};

function AiMessageRow({ message, onClick }) {
  const scheduledText = formatDateTime(message.scheduledAt);
  const sentText = formatDateTime(message.sentAt);

  return (
    <RowContainer
      type="button"
      onClick={() => onClick(message)}
    >
      <TopRow>
        <span className="title">{message.title}</span>
        <span className="type">
          {AI_MESSAGE_TYPE_MAP[message.type] ?? message.type}
        </span>
        <AiMessageStatusBadge status={message.status} />
      </TopRow>

      <Preview>{message.content}</Preview>

      <MetaRow>
        {message.channel && (
          <span>{AI_CHANNEL_MAP[message.channel] ?? message.channel}</span>
        )}
        {scheduledText && (
          <span className="meta-item">
            <Clock size={13} />
            {scheduledText} 예약
          </span>
        )}
        {sentText && (
          <span className="meta-item">
            <Send size={13} />
            {sentText} 발송
          </span>
        )}
        <span>생성 {formatDateTime(message.createdAt)}</span>
      </MetaRow>
    </RowContainer>
  );
}

export default AiMessageRow;
