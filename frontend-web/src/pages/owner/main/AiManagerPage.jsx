import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useLocation } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

import AiManagerReport from '../../../components/owner/ai/AiManagerReport';
import AiCustomerCare from '../../../components/owner/ai/AiCustomerCare';
import AiReviewResponse from '../../../components/owner/ai/AiReviewResponse';
import AiEventPerformance from '../../../components/owner/ai/AiEventPerformance';
import AiLocationMatching from '../../../components/owner/ai/AiLocationMatching';
import AiMarketingAutomation from '../../../components/owner/ai/AiMarketingAutomation';
import AiOperationWarning from '../../../components/owner/ai/AiOperationWarning';
import AiWeeklySummary from '../../../components/owner/ai/AiWeeklySummary';

const PageLayout = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
  padding-bottom: 60px;
`;

const TwoColumnGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const StatusMessage = styled.div`
  text-align: center;
  padding: 100px 0;
  color: ${(props) => (props.$isError ? '#ef4444' : '#64748b')};
  font-size: 15px;
`;

export default function AiManagerPage() {
  const location = useLocation();

  const [dashboardData, setDashboardData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 대시보드 API 호출
  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        const response = await aiManagerApi.getDashboard();

        if (response.data?.success) {
          setDashboardData(response.data.data);
        } else {
          setError(
            response.data?.message || '대시보드 데이터를 불러오지 못했습니다.',
          );
        }
      } catch (err) {
        console.error('대시보드 데이터 조회 오류:', err);
        setError('서버와 통신 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, []);

  // 사이드바 클릭 시 스크롤 이동
  useEffect(() => {
    if (location.hash) {
      const targetId = location.hash.replace('#', '');
      const element = document.getElementById(targetId);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [location, loading]);

  if (loading) {
    return (
      <PageLayout>
        <StatusMessage>AI 매니저 대시보드를 불러오는 중입니다...</StatusMessage>
      </PageLayout>
    );
  }

  if (error) {
    return (
      <PageLayout>
        <StatusMessage $isError>{error}</StatusMessage>
      </PageLayout>
    );
  }

  return (
    <PageLayout>
      {/* 1. AI 점장 보고 */}
      <div
        id="section-ai-report"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiManagerReport
          todoCount={dashboardData?.todoCount}
          reportedAt={dashboardData?.reportedAt}
          privacyNotice={dashboardData?.privacyNotice}
        />
      </div>

      {/* 2. AI 고객 케어 (배열) */}
      <div
        id="section-ai-care"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiCustomerCare data={dashboardData?.customerCareSummaries} />
      </div>

      {/* 3 & 4. 리뷰 대응 / 이벤트 성과 (2단 그리드) */}
      <TwoColumnGrid>
        <div
          id="section-ai-review"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiReviewResponse data={dashboardData?.reviewInquirySummary} />
        </div>
        <div
          id="section-ai-event"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiEventPerformance data={dashboardData?.eventPerformanceSummary} />
        </div>
      </TwoColumnGrid>

      {/* 5 & 6. 생활권 매칭 / 마케팅 자동화 (2단 그리드) */}
      <TwoColumnGrid>
        <div
          id="section-ai-location"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiLocationMatching score={dashboardData?.localMatchScore} />
        </div>
        <div
          id="section-ai-marketing"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiMarketingAutomation />
        </div>
      </TwoColumnGrid>

      {/* 7. 운영 위험 조기경보 */}
      <div
        id="section-ai-warning"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiOperationWarning data={dashboardData?.operationRiskSummary} />
      </div>

      {/* 8. AI 활동 요약 */}
      <div
        id="section-ai-summary"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiWeeklySummary data={dashboardData?.activitySummary} />
      </div>
    </PageLayout>
  );
}
