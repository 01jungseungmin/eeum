import React, { useState } from 'react';
import styled from 'styled-components';
import {
  Star,
  ChevronRight,
  AlertTriangle,
  MessageSquare,
  Megaphone,
  MessageCircle,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import ComplaintReplyModal from '../../../components/owner/ai/modal/ComplaintReplyModal';

export default function AiReviewResponse() {
  const navigate = useNavigate();

  // 모달 통합 상태 관리 (open 여부, 모달 타입, 대상 ID 등)
  const [modalState, setModalState] = useState({
    isOpen: false,
    type: 'review', // 'review' | 'inquiry' | 'complaint'
    targetId: null,
  });

  // 모달 열기 핸들러
  const handleOpenModal = (type, targetId = null) => {
    setModalState({
      isOpen: true,
      type,
      targetId,
    });
  };

  // 모달 닫기 핸들러
  const handleCloseModal = () => {
    setModalState((prev) => ({ ...prev, isOpen: false }));
  };

  return (
    <>
      <CardContainer id="section-ai-review">
        <Header>
          <HeaderLeft>
            <IconBox>
              <Star size={20} />
            </IconBox>
            <TitleArea>
              <h3>리뷰·문의 자동 대응</h3>
              <p>답글 초안 · 반복 불만 감지</p>
            </TitleArea>
          </HeaderLeft>

          <MoreButton onClick={() => navigate('/ai-manager/review')}>
            더보기 <ChevronRight size={16} />
          </MoreButton>
        </Header>

        {/* 1. 반복 불만 경고 박스 */}
        <WarningBox>
          <WarningHeader>
            <AlertTriangle size={16} /> 최근 2주 리뷰에서 '대기 시간' 표현이 5회
            반복됐어요.
          </WarningHeader>
          <WarningSub>별점은 유지 중이지만, 불만이 쌓이고 있습니다.</WarningSub>
          <WarningButtonGroup>
            <ActionBtn
              $primary
              onClick={() => handleOpenModal('complaint', 'complaint-1')}
            >
              <MessageSquare size={14} /> 답글 초안 만들기
            </ActionBtn>
            <ActionBtn onClick={() => navigate('/notices/create')}>
              <Megaphone size={14} /> 공지 문구 만들기
            </ActionBtn>
          </WarningButtonGroup>
        </WarningBox>

        {/* 2. 미답변 리뷰 */}
        <ItemRow>
          <ItemLeft>
            <ItemIconBox
              $bgColor="#fef9c3"
              $color="#ca8a04"
            >
              <Star size={18} />
            </ItemIconBox>
            <ItemText>
              <h4>미답변 리뷰</h4>
              <p>3건 · 답글을 기다리고 있어요</p>
            </ItemText>
          </ItemLeft>
          <OutlineBtn onClick={() => handleOpenModal('review', 'review-1')}>
            답글 초안 보기
          </OutlineBtn>
        </ItemRow>

        {/* 3. 미답변 문의 */}
        <ItemRow>
          <ItemLeft>
            <ItemIconBox
              $bgColor="#e0f2fe"
              $color="#0284c7"
            >
              <MessageCircle size={18} />
            </ItemIconBox>
            <ItemText>
              <h4>미답변 문의</h4>
              <p>2건 · 주차, 영업시간 관련</p>
            </ItemText>
          </ItemLeft>
          <OutlineBtn onClick={() => handleOpenModal('inquiry', 'inquiry-1')}>
            답변 초안 보기
          </OutlineBtn>
        </ItemRow>
      </CardContainer>

      {/* 동적 통합 모달 */}
      <ComplaintReplyModal
        isOpen={modalState.isOpen}
        onClose={handleCloseModal}
        type={modalState.type}
        targetId={modalState.targetId}
      />
    </>
  );
}

// Styled-Components 생략 (기존 스타일 코드 동일 유지)
// 스타일 선언 (이전 동일)
const CardContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;
const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;
const HeaderLeft = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;
const IconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: #fef9c3;
  color: #ca8a04;
  display: flex;
  align-items: center;
  justify-content: center;
`;
const TitleArea = styled.div`
  h3 {
    font-size: 18px;
    font-weight: 700;
    margin: 0;
    color: #111827;
  }
  p {
    font-size: 12px;
    color: #6b7280;
    margin: 4px 0 0;
  }
`;
const MoreButton = styled.button`
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
`;
const WarningBox = styled.div`
  background-color: #fdf2f2;
  border: 1px solid #fde8e8;
  border-radius: 12px;
  padding: 16px;
`;
const WarningHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e02424;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
`;
const WarningSub = styled.p`
  font-size: 12px;
  color: #9b1c1c;
  margin: 0 0 12px 0;
`;
const WarningButtonGroup = styled.div`
  display: flex;
  gap: 10px;
`;
const ActionBtn = styled.button`
  flex: 1;
  background-color: #ffffff;
  border: 1px solid ${(props) => (props.$primary ? '#bbf7d0' : '#e5e7eb')};
  color: ${(props) => (props.$primary ? '#166534' : '#374151')};
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
`;
const ItemRow = styled.div`
  background-color: #fafafa;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;
const ItemLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;
const ItemIconBox = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background-color: ${(props) => props.$bgColor || '#f3f4f6'};
  color: ${(props) => props.$color || '#374151'};
  display: flex;
  align-items: center;
  justify-content: center;
`;
const ItemText = styled.div`
  h4 {
    font-size: 14px;
    font-weight: 700;
    color: #1f2937;
    margin: 0;
  }
  p {
    font-size: 12px;
    color: #6b7280;
    margin: 2px 0 0;
  }
`;
const OutlineBtn = styled.button`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
`;
