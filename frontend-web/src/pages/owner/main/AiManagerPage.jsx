import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  Sparkles,
  Users,
  MessageSquare,
  TrendingUp,
  MapPin,
  ShieldAlert,
  Activity,
  Info,
} from 'lucide-react';

import AiSectionCard from '../../../components/owner/ai/AiSectionCard';
import AiCareCardList from '../../../components/owner/ai/AiCareCardList';
import AiStatGrid from '../../../components/owner/ai/AiStatGrid';
import AiRiskBadge from '../../../components/owner/ai/AiRiskBadge';

import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { AI_EMPTY_TEXT } from '../../../constants/aiConstants';

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 20px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const HeroBanner = styled.div`
  background: #1c5335;
  color: white;
  border-radius: 16px;
  padding: 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .text-side {
    h1 {
      margin: 0 0 8px 0;
      font-size: 24px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    p {
      margin: 0;
      font-size: 14px;
      opacity: 0.85;
    }
  }

  .todo-side {
    text-align: right;

    .label {
      font-size: 13px;
      opacity: 0.85;
      margin-bottom: 4px;
    }
    .count {
      font-size: 32px;
      font-weight: 700;
    }
    .unit {
      font-size: 15px;
      font-weight: 600;
      margin-left: 3px;
      opacity: 0.85;
    }
  }
`;

const NoticeBar = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  background: #ffffff;
  border: 1px solid #eef0f2;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 12px;
  color: #8e94a0;
`;

const GridRow = styled.div`
  display: grid;
  grid-template-columns: ${(props) => props.$columns ?? '1fr 1fr'};
  gap: 20px;
`;

const StateBox = styled.div`
  background: white;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: #8e94a0;
`;

const MatchScore = styled.div`
  display: flex;
  align-items: baseline;
  gap: 4px;

  .score {
    font-size: 32px;
    font-weight: bold;
    color: #00a651;
  }
  .total {
    font-size: 15px;
    font-weight: 600;
    color: #8e94a0;
  }
`;

const RiskHeadline = styled.p`
  margin: 0;
  font-size: 14px;
  color: #4a5568;
  line-height: 1.6;
`;

// 보고 기준 시각을 "오늘 오전 8:00 기준" 형태로 변환
const formatReportedAt = (reportedAt) => {
  if (!reportedAt) return '';

  const date = new Date(reportedAt);
  const isToday = new Date().toDateString() === date.toDateString();
  const time = date.toLocaleTimeString('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
  });

  return `${isToday ? '오늘' : date.toLocaleDateString('ko-KR')} ${time} 기준`;
};

// 비율 값(0~1)을 퍼센트 문자열로 변환. 데이터 부족 시 null 유지
const formatRatio = (ratio) => {
  if (ratio === null || ratio === undefined) return null;
  return (ratio * 100).toFixed(1);
};

function AiManagerPage() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        const response = await aiManagerApi.getDashboard();
        if (response.data.success) {
          setDashboard(response.data.data);
        }
      } catch (err) {
        console.error('AI 매니저 대시보드 조회 실패:', err);
        setError('AI 매니저 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, []);

  if (loading) {
    return (
      <PageContainer>
        <StateBox>AI 매니저가 오늘의 상점 현황을 정리하고 있어요...</StateBox>
      </PageContainer>
    );
  }

  if (error || !dashboard) {
    return (
      <PageContainer>
        <StateBox>{error ?? AI_EMPTY_TEXT}</StateBox>
      </PageContainer>
    );
  }

  const {
    reportedAt,
    todoCount,
    privacyNotice,
    customerCareSummaries,
    reviewInquirySummary,
    eventPerformanceSummary,
    localMatchScore,
    operationRiskSummary,
    activitySummary,
  } = dashboard;

  return (
    <PageContainer>
      <HeroBanner>
        <div className="text-side">
          <h1>
            <Sparkles size={24} />
            AI 매니저
          </h1>
          <p>{formatReportedAt(reportedAt)}</p>
        </div>
        <div className="todo-side">
          <div className="label">오늘 처리할 항목</div>
          <div>
            <span className="count">{todoCount}</span>
            <span className="unit">건</span>
          </div>
        </div>
      </HeroBanner>

      {privacyNotice && (
        <NoticeBar>
          <Info size={14} />
          {privacyNotice}
        </NoticeBar>
      )}

      <AiSectionCard
        icon={<Users size={18} />}
        title="AI 고객 케어"
        subtitle="지금 말을 걸어야 할 고객을 AI가 골라뒀어요"
        onMore={() => navigate('/ai-manager/customer-care')}
      >
        <AiCareCardList
          summaries={customerCareSummaries}
          onSelect={(careType) =>
            navigate(`/ai-manager/customer-care?careType=${careType}`)
          }
        />
      </AiSectionCard>

      <GridRow>
        <AiSectionCard
          icon={<MessageSquare size={18} />}
          title="리뷰/문의 자동 대응"
          subtitle="답변이 밀린 곳을 확인하세요"
          onMore={() => navigate('/ai-manager/review-inquiry')}
        >
          <AiStatGrid
            stats={[
              {
                label: '미답변 리뷰',
                value: reviewInquirySummary?.unansweredReviewCount,
                unit: '건',
              },
              {
                label: '미답변 문의',
                value: reviewInquirySummary?.unansweredInquiryCount,
                unit: '건',
              },
              {
                label: '반복 불만 키워드',
                value: reviewInquirySummary?.complaintKeywordCount,
                unit: '개',
              },
            ]}
          />
        </AiSectionCard>

        <AiSectionCard
          icon={<TrendingUp size={18} />}
          title="이벤트 성과"
          subtitle="진행 중인 이벤트의 반응을 확인하세요"
          onMore={() => navigate('/ai-manager/marketing')}
        >
          <AiStatGrid
            columns={4}
            stats={[
              {
                label: '상품 조회수',
                value: eventPerformanceSummary?.productViewCount,
                unit: '회',
              },
              {
                label: '주문 전환율',
                value: formatRatio(eventPerformanceSummary?.orderConversionRate),
                unit: '%',
              },
              {
                label: '신규 고객 비중',
                value: formatRatio(eventPerformanceSummary?.newCustomerRatio),
                unit: '%',
              },
              {
                label: '단골 재주문',
                value: eventPerformanceSummary?.regularReorderCount,
                unit: '건',
              },
            ]}
          />
        </AiSectionCard>
      </GridRow>

      <GridRow $columns="1fr 1fr 1fr">
        <AiSectionCard
          icon={<MapPin size={18} />}
          title="생활권 매칭"
          subtitle="우리 가게와 동네의 궁합"
          onMore={() => navigate('/ai-manager/local-match')}
        >
          {localMatchScore === null || localMatchScore === undefined ? (
            <RiskHeadline>{AI_EMPTY_TEXT}</RiskHeadline>
          ) : (
            <MatchScore>
              <span className="score">{localMatchScore}</span>
              <span className="total">/ 100점</span>
            </MatchScore>
          )}
        </AiSectionCard>

        <AiSectionCard
          icon={<ShieldAlert size={18} />}
          title="운영 위험 조기정보"
          subtitle="미리 알면 막을 수 있어요"
          extra={<AiRiskBadge level={operationRiskSummary?.riskLevel} />}
          onMore={() => navigate('/ai-manager/operation-risk')}
        >
          <RiskHeadline>
            {operationRiskSummary?.headline ?? AI_EMPTY_TEXT}
          </RiskHeadline>
        </AiSectionCard>

        <AiSectionCard
          icon={<Activity size={18} />}
          title="AI 활동 요약"
          subtitle="최근 30일 동안 AI가 한 일"
          onMore={() => navigate('/ai-manager/messages')}
          moreLabel="메시지 보기"
        >
          <AiStatGrid
            columns={2}
            stats={[
              { label: '초안 생성', value: activitySummary?.draftCount, unit: '건' },
              { label: '발송 처리', value: activitySummary?.sentCount, unit: '건' },
            ]}
          />
          {activitySummary?.highlight && (
            <RiskHeadline>{activitySummary.highlight}</RiskHeadline>
          )}
        </AiSectionCard>
      </GridRow>
    </PageContainer>
  );
}

export default AiManagerPage;
