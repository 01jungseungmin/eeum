import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { X, Sparkles, Send, Check, Loader2 } from 'lucide-react';
import { aiManagerApi } from '../../../../api/owner/aiManagerApi';
import { useAiDraft } from '../../../../hooks/useAiDraft';

// type별 고정 타이틀/버튼 문구 (실제 내용은 AI 초안 생성 API의 content를 그대로 사용)
const MODAL_META = {
  review: { title: '미답변 리뷰 답글 초안', submitLabel: '답글 등록하기' },
  inquiry: { title: '미답변 문의 답변 초안', submitLabel: '답변 보내기' },
  complaint: { title: '반복 불만 답글 초안', submitLabel: '답글 등록하기' },
};

export default function ComplaintReplyModal({
  isOpen,
  onClose,
  targetId,
  type = 'review', // 'review' | 'inquiry' | 'complaint'
  keyword, // complaint 타입일 때 대응할 불만 키워드
  subtitle,
  onSuccess,
}) {
  const meta = MODAL_META[type];

  const createDraftRequest = useCallback(
    (payload) => {
      if (type === 'inquiry') {
        return aiManagerApi.createInquiryReplyDraft(
          targetId,
          payload.confirmDelete,
        );
      }
      if (type === 'complaint') {
        return aiManagerApi.createComplaintDraft({
          keyword,
          confirmDelete: payload.confirmDelete,
        });
      }
      return aiManagerApi.createReviewReplyDraft(
        targetId,
        payload.confirmDelete,
      );
    },
    [type, targetId, keyword],
  );

  const { draft, setDraft, creating, createDraft } =
    useAiDraft(createDraftRequest);

  const [message, setMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    queueMicrotask(() => {
      setIsSuccess(false);
      setMessage('');
      setDraft(null);

      createDraft().then((result) => {
        if (result) setMessage(result.content || '');
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async () => {
    if (!draft?.messageId || isSending) return;
    setIsSending(true);

    try {
      if (message !== draft.content) {
        await aiManagerApi.updateGeneratedMessage(draft.messageId, {
          content: message,
        });
      }
      await aiManagerApi.sendGeneratedMessage(draft.messageId);

      setIsSuccess(true);
      onSuccess?.();
    } catch (error) {
      console.error('초안 전송 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '전송 중 오류가 발생했습니다.',
      );
    } finally {
      setIsSending(false);
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <Header>
          <TitleGroup>
            <Title>{meta.title}</Title>
            {subtitle && <Subtitle>{subtitle}</Subtitle>}
          </TitleGroup>
          <CloseButton onClick={onClose}>
            <X size={18} />
          </CloseButton>
        </Header>

        {!isSuccess ? (
          <>
            <MessageCard>
              <Badge>
                <Sparkles size={14} /> AI 준비 메시지 · 수정 가능
              </Badge>
              {creating ? (
                <LoadingText>
                  <Loader2 size={16} /> AI가 문구를 생성하고 있어요...
                </LoadingText>
              ) : (
                <MessageInput
                  rows={3}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
              )}
            </MessageCard>

            <ButtonGroup>
              <CancelButton
                onClick={onClose}
                disabled={isSending}
              >
                취소
              </CancelButton>
              <SubmitButton
                onClick={handleSubmit}
                disabled={isSending || creating || !draft}
              >
                {isSending ? (
                  <Loader2
                    size={16}
                    style={{ animation: 'spin 1s linear infinite' }}
                  />
                ) : (
                  <>
                    <Send size={16} /> {meta.submitLabel}
                  </>
                )}
              </SubmitButton>
            </ButtonGroup>
          </>
        ) : (
          <>
            <SuccessBody>
              <CheckCircle>
                <Check
                  size={32}
                  strokeWidth={3}
                />
              </CheckCircle>
              <SuccessTitle>
                {type === 'inquiry'
                  ? '답변이 발송 완료되었어요'
                  : '답글이 등록 대기 중이에요'}
              </SuccessTitle>
              <SuccessDescription>
                {type === 'inquiry'
                  ? '고객에게 안내 메시지가 등록되었습니다.'
                  : '사장님이 승인한 답글만 게시됩니다.'}
              </SuccessDescription>
            </SuccessBody>

            <ConfirmButton onClick={onClose}>
              <Check size={18} /> 확인
            </ConfirmButton>
          </>
        )}
      </ModalContainer>
    </Overlay>
  );
}

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background-color: #ffffff;
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;

  @keyframes spin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const TitleGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Title = styled.h2`
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0;
`;

const Subtitle = styled.span`
  font-size: 14px;
  color: #9ca3af;
`;

const CloseButton = styled.button`
  background: #f3f4f6;
  border: none;
  border-radius: 10px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  cursor: pointer;

  &:hover {
    background: #e5e7eb;
    color: #111827;
  }
`;

const MessageCard = styled.div`
  background-color: #f0fdf4;
  border: 1px dashed #86efac;
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const Badge = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #166534;
`;

const LoadingText = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #4b5563;
  padding: 8px 0;
`;

const MessageInput = styled.textarea`
  width: 100%;
  background: transparent;
  border: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  font-family: inherit;
  padding: 0;

  &:focus {
    outline: none;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
`;

const CancelButton = styled.button`
  background: transparent;
  border: none;
  font-size: 14px;
  font-weight: 600;
  color: #6b7280;
  cursor: pointer;
  padding: 10px 16px;

  &:hover {
    color: #111827;
  }
`;

const SubmitButton = styled.button`
  background-color: #41b37d;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  padding: 12px 24px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #369a6a;
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

const SuccessBody = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
  gap: 12px;
  text-align: center;
`;

const CheckCircle = styled.div`
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background-color: #41b37d;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const SuccessTitle = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;
`;

const SuccessDescription = styled.p`
  font-size: 14px;
  color: #6b7280;
  margin: 0;
`;

const ConfirmButton = styled.button`
  width: 100%;
  background-color: #f0fdf4;
  color: #166534;
  border: none;
  border-radius: 12px;
  padding: 14px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  margin-top: 8px;
  transition: background-color 0.2s;

  &:hover {
    background-color: #dcfce7;
  }
`;
