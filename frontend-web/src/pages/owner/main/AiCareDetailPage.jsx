import React from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Users,
  Sparkles,
  ShoppingCart,
  Heart,
  MessageSquare,
  Send,
  Megaphone,
} from 'lucide-react';
import CustomerCareCard from '../../../components/owner/ai/CustomerCareCard';

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  padding-bottom: 40px;
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  width: fit-content;

  &:hover {
    color: #111827;
  }
`;

const HeaderArea = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const HeaderIconBox = styled.div`
  width: 48px;
  height: 48px;
  background-color: #e0e7ff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
  flex-shrink: 0;
`;

const HeaderTitleGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    h1 {
      font-size: 22px;
      font-weight: 800;
      color: #111827;
      margin: 0;
    }
  }

  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
  }
`;

const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #ecfdf5;
  color: #059669;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 12px;
  border: 1px solid #a7f3d0;
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const CardListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SidePanel = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SummaryCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #f3f4f6;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);

  h3 {
    font-size: 13px;
    color: #6b7280;
    margin: 0 0 16px 0;
    font-weight: 600;
  }
`;

const SummaryList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const SummaryRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;

  .label {
    color: #4b5563;
  }

  .value {
    font-weight: 700;
    color: #111827;
  }
`;

const ActionButtonGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const PrimaryButton = styled.button`
  width: 100%;
  padding: 14px;
  background-color: #34d399;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #10b981;
  }
`;

const SecondaryButton = styled.button`
  width: 100%;
  padding: 14px;
  background-color: #ffffff;
  color: #374151;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }
`;

const FooterNote = styled.p`
  font-size: 12px;
  color: #9ca3af;
  margin: 12px 0 0 0;
`;

// 데이터 리스트
const CARE_ITEMS = [
  {
    id: 'card-1',
    icon: ShoppingCart,
    iconBgColor: '#ecfdf5',
    iconColor: '#059669',
    priority: '우선순위 1',
    title: '구매 관심이 높은 고객 2명',
    tag: '장바구니 담기 · 반복 조회',
    description:
      '장바구니에 상품을 담았지만 아직 주문하지 않은 고객, 같은 상품을 여러 번 확인한 고객이에요.',
    message:
      '담아두신 김치찌개 세트가 오늘 점심 포장 할인 중입니다. 필요하실 때 편하게 이용해보세요.',
  },
  {
    id: 'card-2',
    icon: Heart,
    iconBgColor: '#fef2f2',
    iconColor: '#ef4444',
    priority: '우선순위 2',
    title: '한동안 방문이 없는 단골 5명',
    tag: '3주 이상 미방문',
    description: '최근 3주간 주문이 없는 기존 단골 고객이에요.',
    message:
      '오랜만이에요. 자주 찾아주셨던 메뉴가 이번 주 다시 준비되었습니다.',
  },
  {
    id: 'card-3',
    icon: MessageSquare,
    iconBgColor: '#eff6ff',
    iconColor: '#3b82f6',
    priority: '우선순위 3',
    title: '문의 후 망설이는 고객 2명',
    tag: '문의 후 미주문',
    description: '문의까지 했지만 주문으로 이어지지 않은 고객이에요.',
    message:
      '남겨주신 문의 관련해 안내드려요. 궁금하신 점 있으시면 편하게 말씀해 주세요.',
  },
];

export default function AiCareDetailPage() {
  const navigate = useNavigate();

  return (
    <PageContainer>
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      <HeaderArea>
        <HeaderIconBox>
          <Users size={24} />
        </HeaderIconBox>
        <HeaderTitleGroup>
          <div className="title-row">
            <h1>AI 고객 케어 상세</h1>
            <Badge>
              <Sparkles size={12} /> AI 분석 완료
            </Badge>
          </div>
          <p>다시 안내하면 좋을 고객 분석과 AI 준비 메시지 현황을 보여줘요.</p>
        </HeaderTitleGroup>
      </HeaderArea>

      <MainGrid>
        {/* 리스트를 매핑으로 처리 */}
        <CardListContainer>
          {CARE_ITEMS.map((item) => (
            <CustomerCareCard
              key={item.id}
              data={item}
              variant="detail"
            />
          ))}
        </CardListContainer>

        <SidePanel>
          <SummaryCard>
            <h3>이번 주 전송 현황</h3>
            <SummaryList>
              <SummaryRow>
                <span className="label">전송 예약</span>
                <span className="value">2건</span>
              </SummaryRow>
              <SummaryRow>
                <span className="label">대기 중 초안</span>
                <span className="value">1건</span>
              </SummaryRow>
              <SummaryRow>
                <span className="label">자동 제외 고객</span>
                <span className="value">4명</span>
              </SummaryRow>
              <SummaryRow>
                <span className="label">발송 후 재방문</span>
                <span className="value">4명</span>
              </SummaryRow>
            </SummaryList>
          </SummaryCard>

          <ActionButtonGroup>
            <PrimaryButton onClick={() => navigate('/ai-manager/chat')}>
              <Send size={16} /> AI 점장에게 물어보기
            </PrimaryButton>
            <SecondaryButton>
              <Megaphone size={16} /> 공지 문구 만들기
            </SecondaryButton>
          </ActionButtonGroup>
        </SidePanel>
      </MainGrid>

      <FooterNote>
        상세 행동 로그는 노출하지 않고, 요약된 관계 신호만 제공합니다.
      </FooterNote>
    </PageContainer>
  );
}
