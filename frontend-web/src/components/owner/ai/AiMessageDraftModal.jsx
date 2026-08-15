// src/components/owner/ai/AiMessageDraftModal.jsx
// AI가 생성한 초안을 검토·수정하고 발송/예약/취소하는 공용 모달.
// 고객 케어, 마케팅, 리뷰/문의 등 초안 생성 API가 모두 AiGeneratedMessageResponseDto 를
// 반환하므로 이 모달 하나를 재사용한다.
import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { X, RotateCcw, Send, Clock, Ban } from 'lucide-react';

import AiMessageStatusBadge from './AiMessageStatusBadge';
import { aiMessageApi } from '../../../api/owner/aiMessageApi';
import {
  AI_MESSAGE_TYPE_MAP,
  AI_CHANNEL_MAP,
  AI_EDITABLE_STATUSES,
  AI_TRANSITABLE_STATUSES,
} from '../../../constants/aiConstants';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background: #ffffff;
  border-radius: 20px;
  width: 560px;
  max-width: 90%;
  padding: 24px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  position: relative;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .title-group {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  h2 {
    font-size: 18px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0;
  }
  button.close-btn {
    background: none;
    border: none;
    cursor: pointer;
    color: #8e94a0;
    display: flex;
    align-items: center;
    &:hover {
      color: #1a1f2c;
    }
  }
`;

const MetaRow = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 20px;

  span {
    font-size: 12px;
    font-weight: 600;
    color: #4a5568;
    background: #f1f3f5;
    border-radius: 20px;
    padding: 4px 10px;
  }
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;

  label {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }

  input[type='text'],
  input[type='datetime-local'],
  textarea {
    width: 100%;
    padding: 10px 14px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;
    font-family: inherit;
    &:focus {
      border-color: #00a651;
    }
    &:disabled {
      background: #f8f9fa;
      color: #8e94a0;
    }
  }

  textarea {
    min-height: 140px;
    resize: vertical;
    line-height: 1.6;
  }

  .counter {
    align-self: flex-end;
    font-size: 12px;
    color: #8e94a0;
  }
`;

const ResetButton = styled.button`
  display: flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  background: none;
  border: none;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  color: #8e94a0;
  cursor: pointer;

  &:hover {
    color: #1a1f2c;
  }
`;

const GuideText = styled.p`
  margin: 0 0 16px 0;
  font-size: 12px;
  color: #8e94a0;
  line-height: 1.6;
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 8px;
  justify-content: flex-end;
`;

const BaseButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  border: 1px solid transparent;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PrimaryButton = styled(BaseButton)`
  background: #00a651;
  color: white;
  &:hover:not(:disabled) {
    background: #008f45;
  }
`;

const SecondaryButton = styled(BaseButton)`
  background: #ffffff;
  color: #4a5568;
  border-color: #eef0f2;
  &:hover:not(:disabled) {
    border-color: #cbd5e1;
  }
`;

const DangerButton = styled(BaseButton)`
  background: #ffffff;
  color: #ff4d4f;
  border-color: #ffd6d6;
  &:hover:not(:disabled) {
    background: #fff1f0;
  }
`;

// 백엔드가 ErrorCode에 사용자용 한글 문구를 담아 내려주므로 그대로 노출한다
const getErrorMessage = (error, fallback) =>
  error.response?.data?.error?.message ?? fallback;

// LocalDateTime(초 단위)으로 보내야 하므로 datetime-local 값에 초를 붙인다
const toLocalDateTime = (value) => `${value}:00`;

function AiMessageDraftModal({ message, onClose, onUpdated }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [showSchedule, setShowSchedule] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setTitle(message.title ?? '');
    setContent(message.content ?? '');
    setScheduledAt('');
    setShowSchedule(false);
  }, [message]);

  const isNotice = message.type === 'NOTICE';
  const editable = AI_EDITABLE_STATUSES.includes(message.status);
  const transitable = AI_TRANSITABLE_STATUSES.includes(message.status);
  const isDirty = title !== (message.title ?? '') || content !== (message.content ?? '');

  // DRAFT는 곧바로 발송할 수 없어 수정(PATCH)으로 REVIEWED 전환이 선행돼야 한다.
  // 내용을 고치지 않고 보내는 경우에도 동일하므로 여기서 한 번 감싸준다.
  const ensureReviewed = async () => {
    if (message.status === 'DRAFT' || isDirty) {
      const response = await aiMessageApi.updateMessage(message.messageId, {
        title,
        content,
      });
      return response.data.data;
    }
    return message;
  };

  const runAction = async (action, successText) => {
    setSubmitting(true);
    try {
      const result = await action();
      alert(successText);
      onUpdated?.(result);
      onClose();
    } catch (error) {
      console.error('AI 메시지 처리 실패:', error);
      alert(getErrorMessage(error, '처리에 실패했습니다. 잠시 후 다시 시도해주세요.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSave = () =>
    runAction(async () => {
      const response = await aiMessageApi.updateMessage(message.messageId, {
        title,
        content,
      });
      return response.data.data;
    }, '문구를 저장했어요. 검토 완료 상태가 되었습니다.');

  const handleSend = () =>
    runAction(async () => {
      await ensureReviewed();
      const response = isNotice
        ? await aiMessageApi.publishNotice(message.messageId)
        : await aiMessageApi.sendMessage(message.messageId);
      return response.data.data;
    }, isNotice ? '공지를 등록했어요.' : '메시지를 발송했어요.');

  const handleSchedule = () => {
    if (!scheduledAt) {
      alert('예약 발송 시각을 선택해주세요.');
      return;
    }

    return runAction(async () => {
      await ensureReviewed();
      const response = isNotice
        ? await aiMessageApi.scheduleNotice(
            message.messageId,
            toLocalDateTime(scheduledAt),
          )
        : await aiMessageApi.scheduleMessage(
            message.messageId,
            toLocalDateTime(scheduledAt),
          );
      return response.data.data;
    }, '예약 발송을 등록했어요.');
  };

  const handleCancel = () => {
    if (!window.confirm('이 메시지를 취소할까요? 취소하면 되돌릴 수 없습니다.')) {
      return;
    }

    return runAction(async () => {
      const response = await aiMessageApi.cancelMessage(message.messageId);
      return response.data.data;
    }, '메시지를 취소했어요.');
  };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <div className="title-group">
            <h2>AI 초안 검토</h2>
            <AiMessageStatusBadge status={message.status} />
          </div>
          <button
            type="button"
            className="close-btn"
            onClick={onClose}
          >
            <X size={20} />
          </button>
        </ModalHeader>

        <MetaRow>
          <span>{AI_MESSAGE_TYPE_MAP[message.type] ?? message.type}</span>
          {message.channel && (
            <span>{AI_CHANNEL_MAP[message.channel] ?? message.channel}</span>
          )}
        </MetaRow>

        <FormGroup>
          <label htmlFor="ai-message-title">제목</label>
          <input
            id="ai-message-title"
            type="text"
            value={title}
            maxLength={200}
            disabled={!editable || submitting}
            onChange={(e) => setTitle(e.target.value)}
          />
        </FormGroup>

        <FormGroup>
          <label htmlFor="ai-message-content">문구</label>
          <textarea
            id="ai-message-content"
            value={content}
            maxLength={2000}
            disabled={!editable || submitting}
            onChange={(e) => setContent(e.target.value)}
          />
          <div className="counter">{content.length} / 2000</div>
          {editable && message.originalContent !== content && (
            <ResetButton
              type="button"
              onClick={() => setContent(message.originalContent ?? '')}
            >
              <RotateCcw size={13} />
              AI 원본 문구로 되돌리기
            </ResetButton>
          )}
        </FormGroup>

        {showSchedule && (
          <FormGroup>
            <label htmlFor="ai-message-scheduled-at">예약 발송 시각</label>
            <input
              id="ai-message-scheduled-at"
              type="datetime-local"
              value={scheduledAt}
              disabled={submitting}
              onChange={(e) => setScheduledAt(e.target.value)}
            />
          </FormGroup>
        )}

        {!transitable && message.status === 'DRAFT' && (
          <GuideText>
            초안 상태에서는 바로 보낼 수 없어요. 문구를 확인하고 저장하면 검토 완료
            상태가 되며, 보내기 버튼을 누르면 저장 후 발송까지 한 번에 처리됩니다.
          </GuideText>
        )}

        <ButtonRow>
          {transitable && (
            <DangerButton
              type="button"
              disabled={submitting}
              onClick={handleCancel}
            >
              <Ban size={15} />
              취소
            </DangerButton>
          )}
          {editable && (
            <SecondaryButton
              type="button"
              disabled={submitting}
              onClick={handleSave}
            >
              저장
            </SecondaryButton>
          )}
          {editable && (
            <SecondaryButton
              type="button"
              disabled={submitting}
              onClick={
                showSchedule ? handleSchedule : () => setShowSchedule(true)
              }
            >
              <Clock size={15} />
              {showSchedule ? '예약 확정' : '예약 발송'}
            </SecondaryButton>
          )}
          {editable && (
            <PrimaryButton
              type="button"
              disabled={submitting}
              onClick={handleSend}
            >
              <Send size={15} />
              {isNotice ? '공지 등록하기' : '검토 후 보내기'}
            </PrimaryButton>
          )}
        </ButtonRow>
      </ModalContainer>
    </ModalOverlay>
  );
}

export default AiMessageDraftModal;
