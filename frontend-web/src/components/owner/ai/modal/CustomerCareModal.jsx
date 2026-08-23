import React from 'react';
import styled from 'styled-components';
import { X, Check, Users, Sparkles, ShieldCheck, Send } from 'lucide-react';

/* --- 모달 공통 스타일 --- */
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

  p {
    font-size: 13px;
    color: #1f2937;
    margin: 0;
    line-height: 1.5;
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
  step,
  cardData,
  onClose,
  onSubmit,
}) {
  if (!isOpen || !cardData) return null;

  return (
    <ModalOverlay onClick={onClose}>
      {/* 1단계: 검토 모달 */}
      {step === 'review' && (
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
              <span className="highlight">{cardData.title}</span> ·{' '}
              {cardData.bannerText}
            </div>
          </InfoBanner>

          <AiEditableBox>
            <div className="header">
              <Sparkles size={12} /> AI 준비 메시지 · 수정 가능
            </div>
            <p>{cardData.message}</p>
          </AiEditableBox>

          <NoticeBox>
            <ShieldCheck
              size={16}
              color="#059669"
            />
            <span>최근 7일 내 알림을 받은 고객 4명은 제외했습니다.</span>
          </NoticeBox>

          <ModalButtonGroup>
            <CancelBtn onClick={onClose}>취소</CancelBtn>
            <ConfirmBtn onClick={onSubmit}>
              <Send size={14} /> 검토 후 보내기
            </ConfirmBtn>
          </ModalButtonGroup>
        </ModalCard>
      )}

      {/* 2단계: 완료 모달 */}
      {step === 'success' && (
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
            <SuccessTitle>발송 대기 중이에요</SuccessTitle>
            <SuccessSub>승인한 메시지만 고객에게 전송됩니다.</SuccessSub>

            <FullConfirmBtn onClick={onClose}>
              <Check size={14} /> 확인
            </FullConfirmBtn>
          </SuccessModalBody>
        </ModalCard>
      )}
    </ModalOverlay>
  );
}
