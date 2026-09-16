import { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { X, Check, Users, Sparkles, ShieldCheck, Send, Loader2 } from 'lucide-react';
import { aiManagerApi } from '../../../../api/owner/aiManagerApi';
import { useAiDraft } from '../../../../hooks/useAiDraft';

const ModalOverlay = styled.div`
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalCard = styled.div`
  background-color: #ffffff;
  border-radius: 20px;
  width: ${(props) => props.$width || '440px'};
  padding: 24px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 10px 10px -5px rgba(0, 0, 0, 0.04);
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

const CloseButton = styled.button`
  position: absolute;
  top: 20px;
  right: 20px;
  background: #f3f4f6;
  border: none;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  cursor: pointer;
  &:hover {
    background-color: #e5e7eb;
  }
`;

const ModalHeader = styled.div`
  margin-bottom: 20px;
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #111827;
    margin: 0 0 4px 0;
  }
  .priority {
    font-size: 12px;
    color: #9ca3af;
  }
`;

/* --- 1단계: 검토 모달 스타일 --- */
const InfoBanner = styled.div`
  background-color: #eff6ff;
  border-radius: 12px;
  padding: 12px 16px;
  display: flex;
  gap: 10px;
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 16px;
  line-height: 1.4;

  .highlight {
    color: #4f46e5;
    font-weight: 700;
  }
`;

const AiEditableBox = styled.div`
  background-color: #f0fdf4;
  border: 1px dashed #86efac;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;

  .header {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 700;
    color: #16a34a;
    margin-bottom: 8px;
  }
`;

const LoadingText = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4b5563;
  padding: 4px 0;
`;

const MessageInput = styled.textarea`
  width: 100%;
  background: transparent;
  border: none;
  resize: none;
  font-size: 13px;
  line-height: 1.5;
  color: #1f2937;
  font-family: inherit;
  padding: 0;

  &:focus {
    outline: none;
  }
`;

const NoticeBox = styled.div`
  background-color: #f9fafb;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 24px;
`;

const ModalButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
`;

const CancelBtn = styled.button`
  background: none;
  border: none;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 10px 16px;
`;

const ConfirmBtn = styled.button`
  background-color: #3bba84;
  color: #ffffff;
  border: none;
  border-radius: 10px;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  &:hover {
    background-color: #2ea572;
  }
  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

/* --- 2단계: 완료 모달 스타일 --- */
const SuccessModalBody = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 10px 0 0 0;
`;

const SuccessIconCircle = styled.div`
  width: 48px;
  height: 48px;
  background-color: #3bba84;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  margin-bottom: 16px;
`;

const SuccessTitle = styled.h4`
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px 0;
`;

const SuccessSub = styled.p`
  font-size: 12px;
  color: #6b7280;
  margin: 0 0 20px 0;
`;

const FullConfirmBtn = styled.button`
  width: 100%;
  background-color: #f0fdf4;
  color: #16a34a;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;

  &:hover {
    background-color: #dcfce7;
  }
`;

export default function CustomerCareModal({
  isOpen,
  cardData,
  onClose,
  onSuccess,
}) {
  const createDraftRequest = useCallback(
    (payload) => aiManagerApi.createCustomerCareCard(cardData.careType, payload),
    [cardData],
  );

  const { draft, setDraft, creating, createDraft } =
    useAiDraft(createDraftRequest);

  const [message, setMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  // isSending(state) 하나만으로는 막을 수 없는 두 가지 경우를 ref로 막는다:
  // - React StrictMode가 개발 모드에서 mount effect를 두 번 실행 → draft가
  //   두 번 생성될 수 있다.
  // - 버튼을 빠르게 두 번 클릭하면, 두 번째 클릭이 setIsSending(true)가 아직
  //   반영(재렌더)되기 전에 같은 렌더의 오래된 isSending(false)을 그대로
  //   읽어서 가드를 통과한다 — 그 결과 같은 messageId로 /send가 두 번
  //   나가고, 두 번째 요청이 이미 SENT인 메시지를 다시 보내려다 409
  //   (AI_INVALID_STATUS)로 막힌다. ref는 동기적으로 갱신되므로 이 레이스를
  //   막는다.
  const isCreatingDraftRef = useRef(false);
  const isSubmittingRef = useRef(false);

  useEffect(() => {
    if (!isOpen || !cardData) return;
    if (isCreatingDraftRef.current) return;
    isCreatingDraftRef.current = true;

    queueMicrotask(() => {
      setIsSuccess(false);
      setMessage('');
      setDraft(null);

      createDraft()
        .then((result) => {
          if (result) setMessage(result.content || '');
        })
        .finally(() => {
          isCreatingDraftRef.current = false;
        });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, cardData]);

  if (!isOpen || !cardData) return null;

  const handleSubmit = async () => {
    if (!draft?.messageId || isSubmittingRef.current) return;
    isSubmittingRef.current = true;
    setIsSending(true);

    try {
      // 수정 여부와 무관하게 항상 호출해야 한다 — 백엔드는 방금 만들어진
      // DRAFT 상태를 바로 send() 하는 걸 막고, edit()(=이 PATCH 호출)을 거쳐
      // REVIEWED로 전환된 메시지만 보낼 수 있게 해놨다(AiGeneratedMessage.
      // validateTransitable). AI 문구를 안 고치고 그대로 보내는 — 즉
      // message === draft.content인 — 가장 흔한 경로에서만 이 호출을
      // 건너뛰던 게 실제 버그였다.
      await aiManagerApi.updateGeneratedMessage(draft.messageId, {
        content: message,
      });
      await aiManagerApi.sendGeneratedMessage(draft.messageId);

      setIsSuccess(true);
      onSuccess?.();
    } catch (error) {
      console.error('고객 케어 메시지 전송 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '전송 중 오류가 발생했습니다.',
      );
    } finally {
      isSubmittingRef.current = false;
      setIsSending(false);
    }
  };

  return (
    <ModalOverlay onClick={onClose}>
      {/* 1단계: 검토 모달 */}
      {!isSuccess && (
        <ModalCard onClick={(e) => e.stopPropagation()}>
          <CloseButton onClick={onClose}>
            <X size={18} />
          </CloseButton>

          <ModalHeader>
            <h3>{cardData.title}</h3>
            <span className="priority">{cardData.priority}</span>
          </ModalHeader>

          <InfoBanner>
            <Users
              size={18}
              color="#4f46e5"
              style={{ flexShrink: 0 }}
            />
            <div>
              <span className="highlight">
                {cardData.targetCustomerCount ?? 0}명
              </span>{' '}
              에게 안내 메시지를 보낼 준비가 됐어요.
            </div>
          </InfoBanner>

          <AiEditableBox>
            <div className="header">
              <Sparkles size={12} /> AI 준비 메시지 · 수정 가능
            </div>
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
          </AiEditableBox>

          <NoticeBox>
            <ShieldCheck
              size={16}
              color="#059669"
            />
            <span>
              최근 7일 내 알림을 받은 고객{' '}
              {cardData.recentlyNotifiedExcludedCount ?? 0}명은 제외했습니다.
            </span>
          </NoticeBox>

          <ModalButtonGroup>
            <CancelBtn
              onClick={onClose}
              disabled={isSending}
            >
              취소
            </CancelBtn>
            <ConfirmBtn
              onClick={handleSubmit}
              disabled={isSending || creating || !draft}
            >
              {isSending ? (
                <Loader2
                  size={14}
                  style={{ animation: 'spin 1s linear infinite' }}
                />
              ) : (
                <>
                  <Send size={14} /> 검토 후 보내기
                </>
              )}
            </ConfirmBtn>
          </ModalButtonGroup>
        </ModalCard>
      )}

      {/* 2단계: 완료 모달 */}
      {isSuccess && (
        <ModalCard
          $width="380px"
          onClick={(e) => e.stopPropagation()}
        >
          <CloseButton onClick={onClose}>
            <X size={18} />
          </CloseButton>

          <ModalHeader>
            <h3>{cardData.title}</h3>
            <span className="priority">{cardData.priority}</span>
          </ModalHeader>

          <SuccessModalBody>
            <SuccessIconCircle>
              <Check
                size={28}
                strokeWidth={3}
              />
            </SuccessIconCircle>
            <SuccessTitle>발송 완료됐어요</SuccessTitle>
            <SuccessSub>대상 고객에게 메시지가 전송되었습니다.</SuccessSub>

            <FullConfirmBtn onClick={onClose}>
              <Check size={14} /> 확인
            </FullConfirmBtn>
          </SuccessModalBody>
        </ModalCard>
      )}
    </ModalOverlay>
  );
}
