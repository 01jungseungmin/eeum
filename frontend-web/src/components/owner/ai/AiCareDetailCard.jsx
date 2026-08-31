// src/components/owner/ai/AiCareDetailCard.jsx
// 고객 케어 카드 1건. AI 판단 이유 / 준비된 문구를 보여주고 초안 생성을 시작한다.
import React, { useState } from 'react';
import styled from 'styled-components';
import { Sparkles, Info } from 'lucide-react';

import { AI_CARE_TYPE_MAP, AI_CHANNEL_MAP } from '../../../constants/aiConstants';

// 고객 케어는 실제 고객에게 나가는 메시지라 SNS 카드 채널은 제외한다
const SENDABLE_CHANNELS = ['APP_PUSH', 'KAKAO_ALERT', 'STORE_NOTICE'];

const CardContainer = styled.section`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid ${(props) => (props.$highlighted ? '#00a651' : '#eef0f2')};
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  h2 {
    margin: 0 0 6px 0;
    font-size: 15px;
    font-weight: bold;
    color: #1a1f2c;
  }
  .count {
    font-size: 13px;
    color: #8e94a0;

    strong {
      color: #00a651;
      font-size: 18px;
      margin-right: 2px;
    }
  }
`;

const ReasonText = styled.p`
  margin: 0;
  font-size: 13px;
  color: #4a5568;
  line-height: 1.7;
`;

const PreparedMessage = styled.div`
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  font-size: 13px;
  color: #4a5568;
  line-height: 1.7;
  white-space: pre-wrap;
`;

const ExcludedNotice = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8e94a0;
`;

const FormRow = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;

  select,
  input[type='text'] {
    padding: 10px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font-size: 13px;
    outline: none;
    box-sizing: border-box;
    font-family: inherit;
    &:focus {
      border-color: #00a651;
    }
  }
  select {
    width: 150px;
  }
  input[type='text'] {
    flex: 1;
    min-width: 0;
  }
`;

const DraftButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background: #00a651;
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: #008f45;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const DisabledNotice = styled.p`
  margin: 0;
  font-size: 12px;
  color: #8e94a0;
`;

function AiCareDetailCard({ card, highlighted, creating, onCreateDraft }) {
  const [channel, setChannel] = useState('APP_PUSH');
  const [contextHint, setContextHint] = useState('');

  const hasTarget = card.targetCustomerCount > 0;

  return (
    <CardContainer $highlighted={highlighted}>
      <CardHeader>
        <div>
          <h2>{card.title ?? AI_CARE_TYPE_MAP[card.careType]}</h2>
          <div className="count">
            <strong>{card.targetCustomerCount}</strong>명이 대상이에요
          </div>
        </div>
      </CardHeader>

      {card.reason && <ReasonText>{card.reason}</ReasonText>}

      {card.preparedMessage && (
        <PreparedMessage>{card.preparedMessage}</PreparedMessage>
      )}

      {card.recentlyNotifiedExcludedCount > 0 && (
        <ExcludedNotice>
          <Info size={13} />
          최근 7일 내 알림을 받은 {card.recentlyNotifiedExcludedCount}명은 제외했어요
        </ExcludedNotice>
      )}

      {card.sendable && hasTarget ? (
        <FormRow>
          <select
            value={channel}
            aria-label="발송 채널"
            onChange={(e) => setChannel(e.target.value)}
          >
            {SENDABLE_CHANNELS.map((value) => (
              <option
                key={value}
                value={value}
              >
                {AI_CHANNEL_MAP[value]}
              </option>
            ))}
          </select>
          <input
            type="text"
            value={contextHint}
            maxLength={100}
            placeholder="문구에 반영할 힌트 (예: 김치찌개 세트)"
            onChange={(e) => setContextHint(e.target.value)}
          />
          <DraftButton
            type="button"
            disabled={creating}
            onClick={() =>
              onCreateDraft(card.careType, {
                channel,
                contextHint: contextHint.trim() || null,
              })
            }
          >
            <Sparkles size={15} />
            {creating ? '생성 중...' : '초안 만들기'}
          </DraftButton>
        </FormRow>
      ) : (
        <DisabledNotice>
          {hasTarget
            ? '지금은 이 카드로 메시지를 보낼 수 없어요.'
            : '아직 이 조건에 해당하는 고객이 없어요.'}
        </DisabledNotice>
      )}
    </CardContainer>
  );
}

export default AiCareDetailCard;
