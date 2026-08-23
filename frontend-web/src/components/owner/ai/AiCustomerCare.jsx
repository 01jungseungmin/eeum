import React, { useState } from 'react';
import styled from 'styled-components';
import {
  Users,
  ChevronRight,
  ShoppingCart,
  Heart,
  MessageSquare,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import CustomerCareCard from './CustomerCareCard';
import CustomerCareModal from './modal/CustomerCareModal';

const CareContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
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
  background-color: #f0fdf4;
  color: #16a34a;
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
  &:hover {
    color: #374151;
  }
`;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
`;

const FooterInfo = styled.p`
  font-size: 11px;
  color: #9ca3af;
  margin: 16px 0 0;
`;

// 카드 목록 데이터
const CARD_DATA_LIST = [
  {
    id: 'card-1',
    icon: ShoppingCart,
    iconBgColor: '#e0f2fe',
    iconColor: '#0284c7',
    priority: '우선순위 1',
    title: '구매 관심이 높은 고객 2명',
    description:
      '장바구니에 상품을 담았지만 아직 주문하지 않은 고객, 같은 상품을 여러 번 확인한 고객',
    bannerText:
      '장바구니에 상품을 담은 뒤 아직 주문하지 않았고, 이전에 비슷한 메뉴를 이용한 이력이 있는 고객입니다.',
    message:
      '담아두신 김치찌개 세트가 오늘 점심 포장 할인 중입니다. 필요하실 때 편하게 이용해보세요.',
    secondaryAction: false,
  },
  {
    id: 'card-2',
    icon: Heart,
    iconBgColor: '#fce7f3',
    iconColor: '#db2777',
    priority: '우선순위 2',
    title: '한동안 방문이 없는 단골 5명',
    description: '최근 3주간 주문이 없는 기존 단골 고객',
    bannerText: '최근 3주간 주문 이력이 없는 단골 고객입니다.',
    message:
      '오랜만이에요. 자주 찾아주셨던 메뉴가 이번 주 다시 준비되었습니다.',
    secondaryAction: false,
  },
  {
    id: 'card-3',
    icon: MessageSquare,
    iconBgColor: '#f3e8ff',
    iconColor: '#9333ea',
    priority: '우선순위 3',
    title: '문의 후 망설이는 고객 2명',
    description: '문의까지 했지만 주문으로 이어지지 않은 고객',
    message: null,
    secondaryAction: true,
  },
];

export default function AiCustomerCare() {
  const navigate = useNavigate();

  // 모달 상태
  const [modalState, setModalState] = useState({
    isOpen: false,
    step: 'review',
    cardData: null,
  });

  // 발송 완료 상태 저장
  const [sentStatus, setSentStatus] = useState({});

  /* Action 핸들러 */
  const handleCardAction = (cardData) => {
    if (cardData.secondaryAction) {
      // 답변 초안 모달 혹은 페이지 이동 등의 동작 연결
      return;
    }
    if (sentStatus[cardData.id]) return;

    setModalState({
      isOpen: true,
      step: 'review',
      cardData,
    });
  };

  const handleSendSubmit = () => {
    if (modalState.cardData) {
      setSentStatus((prev) => ({ ...prev, [modalState.cardData.id]: true }));
    }
    setModalState((prev) => ({ ...prev, step: 'success' }));
  };

  const handleCloseModal = () => {
    setModalState({ isOpen: false, step: 'review', cardData: null });
  };

  return (
    <CareContainer id="section-ai-care">
      <Header>
        <HeaderLeft>
          <IconBox>
            <Users size={20} />
          </IconBox>
          <TitleArea>
            <h3>AI 고객 케어</h3>
            <p>다시 안내하면 올 고객</p>
          </TitleArea>
        </HeaderLeft>
        <MoreButton onClick={() => navigate('/ai-manager/care')}>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      <CardGrid>
        {CARD_DATA_LIST.map((item) => (
          <CustomerCareCard
            key={item.id}
            data={item}
            isSent={!!sentStatus[item.id]}
            onActionClick={handleCardAction}
          />
        ))}
      </CardGrid>

      <FooterInfo>
        ⓘ 최근 7일 내 알림을 받은 고객 4명은 피로도 방지 차원에서 자동
        제외됐습니다. 상세 행동 로그는 노출하지 않고, 요약된 관계 신호만
        제공합니다.
      </FooterInfo>

      {/* 모달 */}
      <CustomerCareModal
        isOpen={modalState.isOpen}
        step={modalState.step}
        cardData={modalState.cardData}
        onClose={handleCloseModal}
        onSubmit={handleSendSubmit}
      />
    </CareContainer>
  );
}
