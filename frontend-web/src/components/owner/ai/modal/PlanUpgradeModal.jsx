import React from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Crown, X } from 'lucide-react';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background: #ffffff;
  border-radius: 20px;
  width: 90%;
  max-width: 420px;
  padding: 28px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  position: relative;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 16px;
  right: 16px;
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px;

  &:hover {
    color: #374151;
  }
`;

const IconBadge = styled.div`
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background-color: #fef3c7;
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
`;

const ModalTitle = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 8px 0;
`;

const ModalMessage = styled.p`
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 24px 0;
  line-height: 1.5;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 10px;
  width: 100%;
`;

const SecondaryBtn = styled.button`
  flex: 1;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background-color: #ffffff;
  color: #374151;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;

  &:hover {
    background-color: #f9fafb;
  }
`;

const UpgradeBtn = styled.button`
  flex: 1.5;
  padding: 12px;
  border-radius: 10px;
  border: none;
  background-color: #3bba84;
  color: #ffffff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;

  &:hover {
    background-color: #2fa170;
  }
`;

export default function PlanUpgradeModal({ isOpen, onClose, errorMessage }) {
  const navigate = useNavigate();

  if (!isOpen) return null;

  const handleGoToPlan = () => {
    onClose();
    navigate('/ai-manager/plan'); // 플랜 관리 페이지로 이동
  };

  // '나중에 하기' 또는 X 버튼, 배경 클릭 시 모달만 닫기 (페이지 이동 X)
  const handleCloseOnly = () => {
    onClose();
  };

  return (
    <ModalOverlay onClick={handleCloseOnly}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <CloseButton onClick={handleCloseOnly}>
          <X size={20} />
        </CloseButton>

        <IconBadge>
          <Crown size={28} />
        </IconBadge>

        <ModalTitle>플랜 업그레이드 필요</ModalTitle>
        <ModalMessage>
          {errorMessage ||
            '현재 플랜에서 사용할 수 없는 기능입니다. 플랜을 업그레이드하고 모든 AI 매니저 기능을 이용해보세요!'}
        </ModalMessage>

        <ButtonGroup>
          <SecondaryBtn onClick={handleCloseOnly}>나중에 하기</SecondaryBtn>
          <UpgradeBtn onClick={handleGoToPlan}>
            <Crown size={16} /> 플랜 보기
          </UpgradeBtn>
        </ButtonGroup>
      </ModalContainer>
    </ModalOverlay>
  );
}
