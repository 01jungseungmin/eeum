import { useState } from 'react';
import styled from 'styled-components';
import { X, Send } from 'lucide-react';
import { notificationApi } from '../../../api/admin/notificationApi';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: #ffffff;
  width: 100%;
  max-width: 480px;
  border-radius: 16px;
  padding: 28px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1);
  position: relative;
  box-sizing: border-box;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 24px;
  right: 24px;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px 0;
`;

const Subtitle = styled.p`
  font-size: 13px;
  color: #64748b;
  margin: 0 0 24px 0;
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 18px;

  label {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }
  .hint {
    font-size: 11px;
    color: #94a3b8;
  }
`;

const Input = styled.input`
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  font-size: 13px;
`;

const Textarea = styled.textarea`
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  font-size: 13px;
  min-height: 90px;
  resize: vertical;
  font-family: inherit;
`;

const SubmitButton = styled.button`
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 0;
  border-radius: 10px;
  border: none;
  background: #2d5a43;
  color: white;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    background: #244a37;
  }
  &:disabled {
    background: #bfbfbf;
    cursor: not-allowed;
  }
`;

const CONTENT = {
  system: {
    title: '시스템 공지 발송',
    description: '전체 또는 특정 계정에 시스템 공지를 발송합니다.',
    showTargetField: true,
  },
  event: {
    title: '이벤트/마케팅 알림 발송',
    description: '마케팅 수신에 동의한 사용자에게만 발송됩니다.',
    showTargetField: false,
  },
};

function NotificationSendModal({ type, onClose, onSent }) {
  const { title, description, showTargetField } = CONTENT[type];

  const [form, setForm] = useState({
    title: '',
    content: '',
    linkUrl: '',
    targetAccountIds: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (field) => (e) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.title.trim() || !form.content.trim()) {
      alert('제목과 내용을 입력해주세요.');
      return;
    }

    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      linkUrl: form.linkUrl.trim() || undefined,
    };

    if (showTargetField && form.targetAccountIds.trim()) {
      const ids = form.targetAccountIds
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
        .map(Number);
      if (ids.some(Number.isNaN)) {
        alert('발송 대상 accountId는 숫자를 쉼표로 구분해 입력해주세요.');
        return;
      }
      payload.targetAccountIds = ids;
    }

    setSubmitting(true);
    try {
      if (type === 'system') {
        await notificationApi.sendSystemNotice(payload);
        alert('시스템 공지 발송이 시작되었습니다.');
      } else {
        await notificationApi.sendEventNotice(payload);
        alert('이벤트 알림 발송이 시작되었습니다.');
      }
      onSent();
      onClose();
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '알림 발송 중 오류가 발생했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <CloseButton onClick={onClose}>
          <X size={20} />
        </CloseButton>
        <Title>{title}</Title>
        <Subtitle>{description}</Subtitle>

        <form onSubmit={handleSubmit}>
          <FormGroup>
            <label>제목</label>
            <Input
              type="text"
              value={form.title}
              onChange={handleChange('title')}
              placeholder="알림 제목"
              autoFocus
            />
          </FormGroup>

          <FormGroup>
            <label>내용</label>
            <Textarea
              value={form.content}
              onChange={handleChange('content')}
              placeholder="알림 내용"
            />
          </FormGroup>

          <FormGroup>
            <label>딥링크 URL (선택)</label>
            <Input
              type="text"
              value={form.linkUrl}
              onChange={handleChange('linkUrl')}
              placeholder="예: /notices/10"
            />
          </FormGroup>

          {showTargetField && (
            <FormGroup>
              <label>발송 대상 accountId (선택)</label>
              <Input
                type="text"
                value={form.targetAccountIds}
                onChange={handleChange('targetAccountIds')}
                placeholder="예: 1, 2, 3 (비워두면 전체 발송)"
              />
              <span className="hint">
                쉼표로 구분해서 입력하세요. 비워두면 전체 발송됩니다.
              </span>
            </FormGroup>
          )}

          <SubmitButton
            type="submit"
            disabled={submitting}
          >
            <Send size={14} />
            {submitting ? '발송 중...' : '발송하기'}
          </SubmitButton>
        </form>
      </ModalBox>
    </Overlay>
  );
}

export default NotificationSendModal;
