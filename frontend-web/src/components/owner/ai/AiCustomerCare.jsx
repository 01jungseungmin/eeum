import React from 'react';
import styled from 'styled-components';
import {
  Users,
  ChevronRight,
  ShoppingCart,
  Heart,
  MessageSquare,
  Send,
} from 'lucide-react';

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

const Card = styled.div`
  background: #fafafa;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

const CardTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
`;

const CardCategoryIcon = styled.div`
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: ${(props) => props.$bgColor || '#f3f4f6'};
  color: ${(props) => props.$color || '#374151'};
`;

const PriorityBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  color: #15803d;
  background-color: #dcfce7;
  padding: 2px 8px;
  border-radius: 6px;
`;

const CardTitle = styled.h4`
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 6px 0;
`;

const CardDesc = styled.p`
  font-size: 12px;
  color: #6b7280;
  margin: 0 0 16px 0;
  line-height: 1.4;
  height: 34px;
`;

const AiMessageBox = styled.div`
  background-color: #ffffff;
  border: 1px dashed #bbf7d0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
`;

const AiMessageLabel = styled.div`
  font-size: 11px;
  font-weight: 700;
  color: #16a34a;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
`;

const AiMessageText = styled.p`
  font-size: 12px;
  color: #374151;
  margin: 0;
  line-height: 1.45;
`;

const SubmitButton = styled.button`
  width: 100%;
  background-color: ${(props) => (props.$secondary ? '#ffffff' : '#10b981')};
  color: ${(props) => (props.$secondary ? '#374151' : '#ffffff')};
  border: ${(props) => (props.$secondary ? '1px solid #e5e7eb' : 'none')};
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.9;
  }
`;

const FooterInfo = styled.p`
  font-size: 11px;
  color: #9ca3af;
  margin: 16px 0 0;
`;

export default function AiCustomerCare() {
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
        <MoreButton>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      <CardGrid>
        {/* 카드 1 */}
        <Card>
          <div>
            <CardTop>
              <CardCategoryIcon
                $bgColor="#e0f2fe"
                $color="#0284c7"
              >
                <ShoppingCart size={16} />
              </CardCategoryIcon>
              <PriorityBadge>우선순위 1</PriorityBadge>
            </CardTop>
            <CardTitle>구매 관심이 높은 고객 2명</CardTitle>
            <CardDesc>
              장바구니에 상품을 담았지만 아직 주문하지 않은 고객, 같은 상품을
              여러 번 확인한 고객
            </CardDesc>

            <AiMessageBox>
              <AiMessageLabel>✨ AI 준비 메시지</AiMessageLabel>
              <AiMessageText>
                담아두신 김치찌개 세트가 오늘 점심 포장 할인 중입니다. 필요하실
                때 편하게 이용해보세요.
              </AiMessageText>
            </AiMessageBox>
          </div>
          <SubmitButton>
            <Send size={14} /> 검토 후 보내기
          </SubmitButton>
        </Card>

        {/* 카드 2 */}
        <Card>
          <div>
            <CardTop>
              <CardCategoryIcon
                $bgColor="#fce7f3"
                $color="#db2777"
              >
                <Heart size={16} />
              </CardCategoryIcon>
              <PriorityBadge>우선순위 2</PriorityBadge>
            </CardTop>
            <CardTitle>한동안 방문이 없는 단골 5명</CardTitle>
            <CardDesc>최근 3주간 주문이 없는 기존 단골 고객</CardDesc>

            <AiMessageBox>
              <AiMessageLabel>✨ AI 준비 메시지</AiMessageLabel>
              <AiMessageText>
                오랜만이에요. 자주 찾아주셨던 메뉴가 이번 주 다시
                준비되었습니다.
              </AiMessageText>
            </AiMessageBox>
          </div>
          <SubmitButton>
            <Send size={14} /> 검토 후 보내기
          </SubmitButton>
        </Card>

        {/* 카드 3 */}
        <Card>
          <div>
            <CardTop>
              <CardCategoryIcon
                $bgColor="#f3e8ff"
                $color="#9333ea"
              >
                <MessageSquare size={16} />
              </CardCategoryIcon>
              <PriorityBadge>우선순위 3</PriorityBadge>
            </CardTop>
            <CardTitle>문의 후 망설이는 고객 2명</CardTitle>
            <CardDesc>문의까지 했지만 주문으로 이어지지 않은 고객</CardDesc>
          </div>
          <SubmitButton $secondary>답변 초안 보기</SubmitButton>
        </Card>
      </CardGrid>

      <FooterInfo>
        ⓘ 최근 7일 내 알림을 받은 고객 4명은 피로도 방지 차원에서 자동
        제외됐습니다. 상세 행동 로그는 노출하지 않고, 요약된 관계 신호만
        제공합니다.
      </FooterInfo>
    </CareContainer>
  );
}
