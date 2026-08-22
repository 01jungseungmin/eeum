import React from 'react';
import styled from 'styled-components';
import { CreditCard, Check, Minus, Crown } from 'lucide-react';

const CardContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const Header = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;

const IconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: #dcfce7;
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

const GridContainer = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const PlanCard = styled.div`
  position: relative;
  background-color: #ffffff;
  border: 2px solid
    ${(props) =>
      props.$isPro ? '#f59e0b' : props.$isCurrent ? '#34d399' : '#f3f4f6'};
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: ${(props) =>
    props.$isPro ? '0 4px 12px rgba(245, 158, 11, 0.08)' : 'none'};
`;

const BadgeTop = styled.div`
  position: absolute;
  top: -12px;
  left: 24px;
  background-color: ${(props) => props.$bgColor};
  color: ${(props) => props.$textColor};
  font-size: 11px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 12px;
`;

const PlanHeader = styled.div`
  margin-bottom: 20px;
`;

const PlanTitle = styled.h4`
  font-size: 20px;
  font-weight: 800;
  color: #111827;
  margin: 0 0 4px 0;
`;

const PlanSub = styled.p`
  font-size: 12px;
  color: #6b7280;
  margin: 0;
`;

const PriceArea = styled.div`
  margin: 20px 0;
  font-size: 28px;
  font-weight: 800;
  color: #111827;

  span {
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
  }
`;

const BenefitBanner = styled.div`
  background-color: #fffbeb;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: #b45309;
  font-weight: 600;
  margin-bottom: 16px;
`;

const FeatureList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0 0 24px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 1px solid #f3f4f6;
  padding-top: 20px;
`;

const FeatureItem = styled.li`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: ${(props) => (props.$disabled ? '#9ca3af' : '#374151')};
  font-weight: ${(props) => (props.$highlight ? '700' : '500')};
`;

const PlanButton = styled.button`
  width: 100%;
  padding: 12px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;

  ${(props) =>
    props.$variant === 'pro' &&
    `
    background-color: #47a075;
    color: #ffffff;
    border: none;
    &:hover { background-color: #3b8762; }
  `}

  ${(props) =>
    props.$variant === 'current' &&
    `
    background-color: #f0fdf4;
    color: #166534;
    border: none;
    cursor: default;
  `}

  ${(props) =>
    props.$variant === 'outline' &&
    `
    background-color: #ffffff;
    color: #374151;
    border: 1px solid #e5e7eb;
    &:hover { background-color: #f9fafb; }
  `}
`;

const FooterText = styled.p`
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
`;

export default function AiPlanManagement() {
  return (
    <CardContainer id="section-ai-plan">
      {/* 헤더 */}
      <Header>
        <IconBox>
          <CreditCard size={20} />
        </IconBox>
        <TitleArea>
          <h3>플랜 관리</h3>
          <p>
            가게 운영 단계에 맞는 AI 매니저 플랜을 선택하세요. 언제든 변경할 수
            있어요.
          </p>
        </TitleArea>
      </Header>

      {/* 3가지 카드 그리드 */}
      <GridContainer>
        {/* Free 플랜 */}
        <PlanCard>
          <div>
            <PlanHeader>
              <PlanTitle>Free</PlanTitle>
              <PlanSub>기본 운영을 시작하는 사장님</PlanSub>
            </PlanHeader>

            <PriceArea>무료</PriceArea>
            <PlanSub style={{ marginBottom: '20px' }}>
              기본 상품·이벤트 운영을 시작할 수 있어요.
            </PlanSub>

            <FeatureList>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                기본 상품·이벤트 관리
              </FeatureItem>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                월 제한 AI 추천
              </FeatureItem>
              <FeatureItem $disabled>
                <Minus
                  size={16}
                  color="#d1d5db"
                />{' '}
                홍보 문구 생성
              </FeatureItem>
              <FeatureItem $disabled>
                <Minus
                  size={16}
                  color="#d1d5db"
                />{' '}
                AI 고객 케어·리스크 분석
              </FeatureItem>
            </FeatureList>
          </div>

          <PlanButton $variant="outline">Free로 변경</PlanButton>
        </PlanCard>

        {/* AI Basic 플랜 (현재 사용 중) */}
        <PlanCard $isCurrent>
          <BadgeTop
            $bgColor="#bbf7d0"
            $textColor="#166534"
          >
            현재 플랜
          </BadgeTop>

          <div>
            <PlanHeader>
              <PlanTitle>AI Basic</PlanTitle>
              <PlanSub>반복 업무를 줄이고 싶은 사장님</PlanSub>
            </PlanHeader>

            <PriceArea>
              19,000원 <span>/월</span>
            </PriceArea>
            <PlanSub style={{ marginBottom: '20px' }}>
              반복되는 문구 작성과 기본 추천을 줄여줘요.
            </PlanSub>

            <FeatureList>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                기본 상품·이벤트 관리
              </FeatureItem>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                AI 추천 월 30회
              </FeatureItem>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                이벤트 추천 · 홍보 문구 생성
              </FeatureItem>
              <FeatureItem>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                기본 생활권 매칭 분석
              </FeatureItem>
              <FeatureItem $disabled>
                <Minus
                  size={16}
                  color="#d1d5db"
                />{' '}
                AI 고객 케어·리스크 고급 분석
              </FeatureItem>
            </FeatureList>
          </div>

          <PlanButton $variant="current">현재 사용 중</PlanButton>
        </PlanCard>

        {/* AI Pro 플랜 (추천) */}
        <PlanCard $isPro>
          <BadgeTop
            $bgColor="#f59e0b"
            $textColor="#ffffff"
          >
            추천
          </BadgeTop>

          <div>
            <PlanHeader>
              <PlanTitle>AI Pro</PlanTitle>
              <PlanSub>
                고객 이탈과 운영 리스크를 놓치고 싶지 않은 사장님
              </PlanSub>
            </PlanHeader>

            <BenefitBanner>
              놓치는 고객과 운영 리스크를 먼저 잡아드려요.
            </BenefitBanner>

            <PriceArea>
              39,000원 <span>/월</span>
            </PriceArea>
            <PlanSub style={{ marginBottom: '20px' }}>
              고객 이탈, 생활권 노출, 에너지·안전 리스크까지 관리해요.
            </PlanSub>

            <FeatureList>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                AI 고객 케어 고급 분석
              </FeatureItem>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                생활권 매칭 우선 노출
              </FeatureItem>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                리뷰·문의 위험 신호 분석
              </FeatureItem>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                에너지·안전 리스크 고급 분석
              </FeatureItem>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                AI 활동 요약 리포트
              </FeatureItem>
              <FeatureItem $highlight>
                <Check
                  size={16}
                  color="#16a34a"
                />{' '}
                우선 고객 지원
              </FeatureItem>
            </FeatureList>
          </div>

          <PlanButton $variant="pro">
            <Crown size={16} /> Pro 업그레이드
          </PlanButton>
        </PlanCard>
      </GridContainer>

      <FooterText>
        플랜은 매월 갱신되며, 다운그레이드 시 다음 결제일부터 적용됩니다. ·
        결제는 데모에 포함되지 않았어요.
      </FooterText>
    </CardContainer>
  );
}
