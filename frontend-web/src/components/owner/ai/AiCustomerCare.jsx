import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Users, ChevronRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { CARE_TYPE_CONFIG } from '../../../constants/aiManager';

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

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const FooterInfo = styled.p`
  font-size: 11px;
  color: #9ca3af;
  margin: 16px 0 0;
`;

const LoadingText = styled.div`
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
`;

export default function AiCustomerCare() {
  const navigate = useNavigate();

  const [careList, setCareList] = useState([]);
  const [loading, setLoading] = useState(true);

  const [modalState, setModalState] = useState({
    isOpen: false,
    step: 'review',
    cardData: null,
  });
  const [sentStatus, setSentStatus] = useState({});

  // 컴포넌트 마운트 시 전체 조회 API 호출
  useEffect(() => {
    const fetchCustomerCareCards = async () => {
      try {
        setLoading(true);
        const res = await aiManagerApi.getCustomerCareCards();

        // Response 구조: { success: true, data: [ ... ] }
        const resultData = res?.data?.data || res?.data || [];
        if (Array.isArray(resultData)) {
          setCareList(resultData);
        }
      } catch (error) {
        console.error('AI 고객 케어 목록 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchCustomerCareCards();
  }, []);

  const handleCardAction = (cardData) => {
    if (cardData.secondaryAction) {
      navigate('/ai-manager/care');
      return;
    }
    if (sentStatus[cardData.id] || !cardData.sendable) return;

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

  // 피로도 제외 인원 수 추출 (첫 번째 카드 데이터 기준)
  const excludedCount = careList[0]?.recentlyNotifiedExcludedCount ?? 0;

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

      {loading ? (
        <LoadingText>고객 케어 데이터를 불러오는 중입니다...</LoadingText>
      ) : (
        <CardGrid>
          {careList.map((item, index) => {
            const config =
              CARE_TYPE_CONFIG[item.careType] || CARE_TYPE_CONFIG.CART_INTEREST;
            const cardId = item.careType || `care-card-${index}`;

            const formattedData = {
              id: cardId,
              careType: item.careType,
              icon: config.icon,
              iconBgColor: config.iconBgColor,
              iconColor: config.iconColor,
              priority: item.priority
                ? `우선순위 ${item.priority}`
                : `우선순위 ${index + 1}`,
              title: `${item.title || config.defaultTitle} ${item.targetCustomerCount ?? 0}명`,
              description: item.reason || config.defaultDesc, // API: reason (사유/설명)
              message: item.preparedMessage || null, // API: preparedMessage (AI 작성 메시지)
              sendable: item.sendable ?? false,
              secondaryAction:
                item.careType === 'INQUIRY_HESITATION' ||
                config.secondaryAction,
              targetCustomerCount: item.targetCustomerCount ?? 0,
            };

            return (
              <CustomerCareCard
                key={cardId}
                data={formattedData}
                isSent={!!sentStatus[cardId]}
                onActionClick={handleCardAction}
              />
            );
          })}
        </CardGrid>
      )}

      <FooterInfo>
        ⓘ 최근 7일 내 알림을 받은 고객 {excludedCount}명은 피로도 방지 차원에서
        자동 제외됐습니다. 상세 행동 로그는 노출하지 않고, 요약된 관계 신호만
        제공합니다.
      </FooterInfo>

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
