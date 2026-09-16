import { useState, useEffect } from 'react';
import styled from 'styled-components';
import {
  BarChart3,
  MessageSquare,
  MessageCircle,
  Heart,
  UserMinus,
  Repeat,
  ShoppingBag,
  CheckCircle,
  Crown,
} from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { useAuth } from '../../../contexts/AuthContext';
import {
  AI_PAGE_REQUIRED_PLAN,
  hasRequiredPlan,
} from '../../../constants/aiPlanFeatures';
import PlanUpgradeModal from './modal/PlanUpgradeModal';

const CardContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  background-color: #e0f2fe;
  color: #0284c7;
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
    color: #9ca3af;
    margin: 4px 0 0;
  }
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;

  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const StatCard = styled.div`
  background-color: #fafafa;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const StatIconBox = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background-color: ${(props) => props.$bgColor};
  color: ${(props) => props.$color};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const StatText = styled.div`
  .val {
    font-size: 18px;
    font-weight: 800;
    color: #111827;
    span {
      font-size: 13px;
      font-weight: 500;
      color: #6b7280;
      margin-left: 2px;
    }
  }
  .label {
    font-size: 12px;
    color: #6b7280;
    margin-top: 2px;
  }
`;

const ResponseSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const SectionTitle = styled.h4`
  font-size: 12px;
  font-weight: 700;
  color: #9ca3af;
  margin: 0;
`;

const ResponseRow = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const RowLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #1f2937;
`;

const RowValue = styled.div`
  font-size: 14px;
  font-weight: 800;
  color: #15803d;
`;

const FooterText = styled.p`
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
`;

const LoadingText = styled.div`
  font-size: 14px;
  color: #6b7280;
  padding: 20px 0;
  text-align: center;
`;

const LockedButton = styled.button`
  width: 100%;
  background-color: #47a075;
  color: #ffffff;
  border: none;
  padding: 12px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #3b8762;
  }
`;

export default function AiWeeklySummary() {
  const { aiPlanType } = useAuth();
  const [summaryData, setSummaryData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [planLocked, setPlanLocked] = useState(false);
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);

  useEffect(() => {
    // 캐싱된 플랜으로 이미 BASIC 미만인 게 확실하면 요청 자체를 생략한다 —
    // 안 그러면 403 + 브라우저/우리 콘솔에 에러 로그가 매번 찍힌다.
    if (!hasRequiredPlan(aiPlanType, AI_PAGE_REQUIRED_PLAN.activitySummary)) {
      queueMicrotask(() => {
        setPlanLocked(true);
        setLoading(false);
      });
      return;
    }

    const fetchSummary = async () => {
      try {
        const response = await aiManagerApi.getActivitySummary();

        if (response.data.success) {
          setSummaryData(response.data.data);
        }
      } catch (error) {
        const status = error.response?.status;
        const errorData = error.response?.data?.error || error.response?.data;
        if (status === 403 || errorData?.code === 'AI_001') {
          setPlanLocked(true);
        } else {
          console.error('AI 활동 요약 데이터를 불러오는 중 오류 발생:', error);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchSummary();
  }, [aiPlanType]);

  if (loading) {
    return (
      <CardContainer id="section-ai-summary">
        <LoadingText>데이터를 불러오는 중입니다...</LoadingText>
      </CardContainer>
    );
  }

  if (planLocked) {
    return (
      <>
        <CardContainer id="section-ai-summary">
          <Header>
            <IconBox>
              <BarChart3 size={20} />
            </IconBox>
            <TitleArea>
              <h3>AI 점장 활동 요약</h3>
              <p>베이직 플랜부터 이용할 수 있는 기능이에요.</p>
            </TitleArea>
          </Header>
          <LockedButton onClick={() => setIsUpgradeModalOpen(true)}>
            <Crown size={16} /> 플랜 업그레이드
          </LockedButton>
        </CardContainer>
        <PlanUpgradeModal
          isOpen={isUpgradeModalOpen}
          onClose={() => setIsUpgradeModalOpen(false)}
          errorMessage="베이직 플랜부터 이용할 수 있는 기능이에요."
        />
      </>
    );
  }

  return (
    <CardContainer id="section-ai-summary">
      {/* 헤더 */}
      <Header>
        <IconBox>
          <BarChart3 size={20} />
        </IconBox>
        <TitleArea>
          <h3>AI 점장 활동 요약</h3>
          <p>
            {summaryData?.period || '최근 30일'} · AI가 처리한 일과 고객 반응
          </p>
        </TitleArea>
      </Header>

      {/* 활동 수치 그리드 */}
      <StatsGrid>
        <StatCard>
          <StatIconBox
            $bgColor="#fef3c7"
            $color="#d97706"
          >
            <MessageSquare size={18} />
          </StatIconBox>
          <StatText>
            <div className="val">
              {summaryData?.reviewReplyDraftCount ?? 0}
              <span>건</span>
            </div>
            <div className="label">리뷰 답글 초안</div>
          </StatText>
        </StatCard>

        <StatCard>
          <StatIconBox
            $bgColor="#e0f2fe"
            $color="#0284c7"
          >
            <MessageCircle size={18} />
          </StatIconBox>
          <StatText>
            <div className="val">
              {summaryData?.inquiryReplyDraftCount ?? 0}
              <span>건</span>
            </div>
            <div className="label">문의 답변 초안</div>
          </StatText>
        </StatCard>

        <StatCard>
          <StatIconBox
            $bgColor="#ffe4e6"
            $color="#e11d48"
          >
            <Heart size={18} />
          </StatIconBox>
          <StatText>
            <div className="val">
              {summaryData?.regularMessageCount ?? 0}
              <span>건</span>
            </div>
            <div className="label">단골 메시지</div>
          </StatText>
        </StatCard>

        <StatCard>
          <StatIconBox
            $bgColor="#f3e8ff"
            $color="#9333ea"
          >
            <UserMinus size={18} />
          </StatIconBox>
          <StatText>
            <div className="val">
              {summaryData?.inactiveAlertCount ?? 0}
              <span>건</span>
            </div>
            <div className="label">이탈 고객 알림</div>
          </StatText>
        </StatCard>
      </StatsGrid>

      {/* 고객 반응 목록 */}
      <ResponseSection>
        <SectionTitle>AI 점장 활동 이후 고객 반응</SectionTitle>

        <ResponseRow>
          <RowLeft>
            <Repeat
              size={18}
              color="#16a34a"
            />
            단골 메시지 발송 후 재방문
          </RowLeft>
          <RowValue>{summaryData?.revisitAfterMessageCount ?? 0}명</RowValue>
        </ResponseRow>

        <ResponseRow>
          <RowLeft>
            <ShoppingBag
              size={18}
              color="#16a34a"
            />
            이벤트 알림 후 주문 전환
          </RowLeft>
          <RowValue>
            {summaryData?.orderConversionAfterEventCount ?? 0}건
          </RowValue>
        </ResponseRow>

        <ResponseRow>
          <RowLeft>
            <CheckCircle
              size={18}
              color="#16a34a"
            />
            미답변 문의
          </RowLeft>
          <RowValue>
            {summaryData?.unansweredInquiryRemainingCount ?? 0}건 유지
          </RowValue>
        </ResponseRow>
      </ResponseSection>

      <FooterText>
        AI 점장 활동 이후 확인된 고객 반응입니다. 확정 매출로 단정하지 않습니다.
      </FooterText>
    </CardContainer>
  );
}
