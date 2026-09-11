import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { X, Sparkles, ShieldCheck, Send, Check, Loader2 } from 'lucide-react';
import { aiManagerApi } from '../../../../api/owner/aiManagerApi';

export default function ComplaintReplyModal({
  isOpen,
  onClose,
  targetId,
  type = 'review', // 'review' | 'inquiry' | 'complaint'
  title,
  subtitle,
  initialMessage,
  submitLabel,
  excludedCount = 4,
}) {
  // type별 기본 텍스트 설정
  const modalConfig = {
    review: {
      title: title || '미답변 리뷰 답글 초안',
      subtitle: subtitle || '리뷰 3건',
      initial:
        initialMessage ||
        '소중한 후기 감사합니다! 말씀해 주신 점 참고해 더 맛있는 반찬으로 보답하겠습니다. 또 찾아주세요 😊',
      submitLabel: submitLabel || '답글 등록하기',
    },
    inquiry: {
      title: title || '미답변 문의 답변 초안',
      subtitle: subtitle || '주차·영업시간',
      initial:
        initialMessage ||
        '문의 주셔서 감사합니다. 매장 앞 공영주차장 이용 가능하시며, 영업시간은 오전 10시~오후 8시입니다. 방문 기다릴게요!',
      submitLabel: submitLabel || '답변 보내기',
    },
    complaint: {
      title: title || '반복 불만 답글 초안',
      subtitle: subtitle || '‘대기 시간’ 관련',
      initial:
        initialMessage ||
        '기다리시게 해 죄송합니다. 조리 동선을 점검해 대기 시간을 줄이도록 준비 중이에요. 늘 찾아주셔서 감사합니다.',
      submitLabel: submitLabel || '답글 등록하기',
    },
  }[type];

  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setMessage(modalConfig.initial);
      setIsSuccess(false);
      setIsLoading(false);
    }
  }, [isOpen, modalConfig.initial]);

  if (!isOpen) return null;

  // 등록 및 API 호출 처리
  const handleSubmit = async () => {
    if (isLoading) return;
    setIsLoading(true);

    try {
      // type에 따라 분기 처리 가능
      if (type === 'inquiry') {
        // await aiManagerApi.createInquiryReplyDraft(targetId, message);
      } else {
        await aiManagerApi.createReviewReplyDraft(targetId);
      }

      setIsSuccess(true);
    } catch (error) {
      console.error('초안 처리 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '답변 등록 중 오류가 발생했습니다.',
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Overlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <Header>
          <TitleGroup>
            <Title>{modalConfig.title}</Title>
            <Subtitle>{modalConfig.subtitle}</Subtitle>
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
              <MessageInput
                rows={3}
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />
            </MessageCard>

            <NoticeBox>
              <ShieldCheck
                size={16}
                color="#16a34a"
              />
              <span>
                최근 7일 내 알림을 받은 고객 {excludedCount}명은 제외했습니다.
              </span>
            </NoticeBox>

            <ButtonGroup>
              <CancelButton
                onClick={onClose}
                disabled={isLoading}
              >
                취소
              </CancelButton>
              <SubmitButton
                onClick={handleSubmit}
                disabled={isLoading}
              >
                {isLoading ? (
                  <Loader2
                    size={16}
                    style={{ animation: 'spin 1s linear infinite' }}
                  />
                ) : (
                  <>
                    <Send size={16} /> {modalConfig.submitLabel}
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

// --- Dynamic Keyframe Animation & Styled Components ---
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

const NoticeBox = styled.div`
  background-color: #f9fafb;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4b5563;
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
