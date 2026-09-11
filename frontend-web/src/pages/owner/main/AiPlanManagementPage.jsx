import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, CreditCard, Check } from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import * as PortOne from '@portone/browser-sdk/v2';

const PageLayout = styled.div`
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  width: fit-content;
  &:hover {
    color: #111827;
  }
`;

const HeaderSection = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background-color: #f9fafb;
  border-radius: 12px;
`;

const IconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background-color: #e6f4ed;
  color: #2e7d5d;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const TitleBox = styled.div`
  display: flex;
  flex-direction: column;
  h2 {
    font-size: 20px;
    font-weight: 800;
    color: #111827;
    margin: 0 0 4px 0;
  }
  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
  }
`;

const UsageCard = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
`;

const UsageHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: #111827;
  }
`;

const ProgressBarContainer = styled.div`
  width: 100%;
  height: 12px;
  background-color: #f3f4f6;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 8px;
`;

const ProgressBarFill = styled.div`
  height: 100%;
  width: ${({ $percentage }) => $percentage}%;
  background-color: ${({ $percentage }) =>
    $percentage > 90 ? '#ef4444' : '#47a075'};
  transition: width 0.3s ease;
`;

const UsageText = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #6b7280;
  span.bold {
    font-weight: 700;
    color: #111827;
  }
`;

const PlansGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
`;

const PlanCard = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 28px 24px;
  border: 2px solid ${({ $isCurrent }) => ($isCurrent ? '#47a075' : '#f0f0f0')};
  box-shadow: ${({ $isCurrent }) =>
    $isCurrent ? '0 4px 16px rgba(71, 160, 117, 0.15)' : 'none'};
  display: flex;
  flex-direction: column;
  position: relative;
`;

const CurrentBadge = styled.span`
  position: absolute;
  top: -12px;
  right: 20px;
  background-color: #47a075;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 12px;
`;

const PlanName = styled.h4`
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 12px 0;
`;

const PlanPrice = styled.div`
  font-size: 28px;
  font-weight: 800;
  color: #111827;
  margin-bottom: 20px;
  span {
    font-size: 14px;
    font-weight: 400;
    color: #6b7280;
  }
`;

const FeatureList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0 0 24px 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const FeatureItem = styled.li`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #4b5563;
`;

const PlanButton = styled.button`
  width: 100%;
  padding: 12px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  cursor: ${({ $isCurrent, disabled }) =>
    $isCurrent || disabled ? 'default' : 'pointer'};
  border: none;
  background-color: ${({ $isCurrent }) => ($isCurrent ? '#f3f4f6' : '#47a075')};
  color: ${({ $isCurrent }) => ($isCurrent ? '#9ca3af' : '#ffffff')};
  transition: background-color 0.2s;

  &:hover {
    background-color: ${({ $isCurrent, disabled }) =>
      $isCurrent || disabled ? '#f3f4f6' : '#3b8762'};
  }
`;

export default function AiPlanManagementPage() {
  const navigate = useNavigate();
  const [planData, setPlanData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [subscribing, setSubscribing] = useState(false);
  const [paymentLoading, setPaymentLoading] = useState(false);

  // 플랜 데이터 재조회 함수
  const fetchPlans = async () => {
    try {
      setLoading(true);
      const response = await aiManagerApi.getPlans();
      if (response.data?.success) {
        setPlanData(response.data.data);
      }
    } catch (error) {
      console.error('플랜 관리 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPlans();
  }, []);

  const STORE_ID = import.meta.env.VITE_PORTONE_STORE_ID;
  const CHANNEL_KEY = import.meta.env.VITE_PORTONE_CHANNEL_KEY;

  const handleSubscribe = async (plan) => {
    if (paymentLoading) {
      return;
    }

    try {
      setPaymentLoading(true);

      // STEP 1. 우리 서버에 구독 결제 생성 요청
      const response = await aiManagerApi.requestSubscribe(plan.planType);

      if (!response.data?.success) {
        throw new Error(
          response.data?.message || '구독 결제 요청에 실패했습니다.',
        );
      }

      const { paymentId, amount, planType } = response.data.data;

      if (!paymentId) {
        throw new Error('결제 ID를 생성하지 못했습니다.');
      }

      const storeId = sessionStorage.getItem('storeId');

      // STEP 2. PortOne 카카오페이 빌링키 발급

      const issueResponse = await PortOne.requestIssueBillingKey({
        // ⚠️ 이건 PortOne의 Store ID
        storeId: import.meta.env.VITE_PORTONE_STORE_ID,

        // ⚠️ 이건 카카오페이 Channel Key
        channelKey: import.meta.env.VITE_PORTONE_CHANNEL_KEY,

        issueId: `${paymentId}-billing`,
        issueName: `${plan.displayName} 월간 구독`,

        billingKeyMethod: 'EASY_PAY',

        customer: {
          customerId: String(storeId),
        },
      });

      // 사용자가 결제창에서 취소
      if (issueResponse?.code) {
        throw new Error(
          issueResponse.message || '빌링키 발급이 취소되었습니다.',
        );
      }

      const billingKey = issueResponse?.billingKey;

      if (!billingKey) {
        throw new Error('빌링키를 발급받지 못했습니다.');
      }

      // // STEP 3. 빌링키를 백엔드로 전달
      // const billingResponse = await aiManagerApi.requestBillingKeyPayment({
      //   paymentId,
      //   billingKey,
      // });

      // if (!billingResponse.data?.success) {
      //   throw new Error(
      //     billingResponse.data?.message || '첫 결제에 실패했습니다.',
      //   );
      // }

      // STEP 4. 서버에서 결제 검증
      const completeResponse = await aiManagerApi.completeSubscribe(paymentId);

      if (!completeResponse.data?.success) {
        throw new Error(
          completeResponse.data?.message || '결제 검증에 실패했습니다.',
        );
      }

      // STEP 5. 성공
      alert(`${plan.displayName} 월간 구독이 완료되었습니다.`);

      // 플랜 정보 다시 조회
      await fetchPlans();
    } catch (error) {
      console.error('AI 플랜 구독 실패:', error);

      const message =
        error?.response?.data?.message ||
        error?.message ||
        '구독 결제 중 오류가 발생했습니다.';

      alert(message);
    } finally {
      setPaymentLoading(false);
    }
  };

  if (loading) return <div>플랜 정보를 불러오는 중입니다...</div>;
  if (!planData) return <div>플랜 정보를 불러올 수 없습니다.</div>;

  const { currentPlan, plans, currentUsage, monthlyLimit } = planData;
  const usagePercentage = Math.min(
    Math.round((currentUsage / monthlyLimit) * 100),
    100,
  );

  return (
    <PageLayout>
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={18} /> AI 매니저로 돌아가기
      </BackButton>

      <HeaderSection>
        <IconBox>
          <CreditCard size={24} />
        </IconBox>
        <TitleBox>
          <h2>플랜 관리</h2>
          <p>
            가게 운영 단계에 맞는 AI 매니저 플랜을 선택하세요. 언제든 변경할 수
            있어요.
          </p>
        </TitleBox>
      </HeaderSection>

      <UsageCard>
        <UsageHeader>
          <h3>이번 달 사용량</h3>
          <span style={{ fontSize: '13px', color: '#6b7280' }}>
            현재 플랜: <b>{currentPlan}</b>
          </span>
        </UsageHeader>

        <ProgressBarContainer>
          <ProgressBarFill $percentage={usagePercentage} />
        </ProgressBarContainer>

        <UsageText>
          <span>
            사용 <span className="bold">{currentUsage}</span>회 / 월{' '}
            {monthlyLimit}회
          </span>
          <span className="bold">{usagePercentage}% 사용됨</span>
        </UsageText>
      </UsageCard>

      <PlansGrid>
        {plans?.map((plan) => {
          const isCurrent = plan.planType === currentPlan;

          return (
            <PlanCard
              key={plan.planType}
              $isCurrent={isCurrent}
            >
              {isCurrent && <CurrentBadge>이용 중인 플랜</CurrentBadge>}

              <PlanName>{plan.displayName}</PlanName>
              <PlanPrice>
                {plan.monthlyPrice.toLocaleString()}원 <span>/ 월</span>
              </PlanPrice>

              <FeatureList>
                {plan.features?.map((feature, idx) => (
                  <FeatureItem key={idx}>
                    <Check
                      size={16}
                      color="#47a075"
                    />
                    {feature}
                  </FeatureItem>
                ))}
              </FeatureList>

              <PlanButton
                $isCurrent={isCurrent}
                disabled={isCurrent || subscribing}
                onClick={() => handleSubscribe(plan)}
              >
                {isCurrent
                  ? '사용 중'
                  : subscribing
                    ? '처리 중...'
                    : '플랜 변경하기'}
              </PlanButton>
            </PlanCard>
          );
        })}
      </PlansGrid>
    </PageLayout>
  );
}
