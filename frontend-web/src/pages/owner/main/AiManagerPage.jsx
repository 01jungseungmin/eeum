import React, { useEffect } from 'react';
import styled from 'styled-components';
import { useLocation } from 'react-router-dom';

import AiManagerReport from '../../../components/owner/ai/AiManagerReport';
import AiCustomerCare from '../../../components/owner/ai/AiCustomerCare';
import AiReviewResponse from '../../../components/owner/ai/AiReviewResponse';
import AiEventPerformance from '../../../components/owner/ai/AiEventPerformance';
import AiLocationMatching from '../../../components/owner/ai/AiLocationMatching';
import AiMarketingAutomation from '../../../components/owner/ai/AiMarketingAutomation';
import AiOperationWarning from '../../../components/owner/ai/AiOperationWarning';
import AiWeeklySummary from '../../../components/owner/ai/AiWeeklySummary';
import AiPlanManagement from '../../../components/owner/ai/AiPlanManagement';

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

export default function AiManagerPage() {
  const location = useLocation();

  // 사이드바 클릭 시 해당 섹션으로 부드러운 스크롤 이동
  useEffect(() => {
    if (location.hash) {
      const targetId = location.hash.replace('#', '');
      const element = document.getElementById(targetId);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [location]);

  return (
    <PageLayout>
      {/* 1. AI 점장 보고 */}
      <div
        id="section-ai-report"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiManagerReport />
      </div>

      {/* 2. AI 고객 케어 */}
      <div
        id="section-ai-care"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiCustomerCare />
      </div>

      {/* 3 & 4. 리뷰 대응 / 이벤트 성과 (2단 그리드) */}
      <TwoColumnGrid>
        <div
          id="section-ai-review"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiReviewResponse />
        </div>
        <div
          id="section-ai-event"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiEventPerformance />
        </div>
      </TwoColumnGrid>

      {/* 5 & 6. 생활권 매칭 / 마케팅 자동화 (2단 그리드) */}
      <TwoColumnGrid>
        <div
          id="section-ai-location"
          style={{ scrollMarginTop: '24px' }}
        >
          <AiLocationMatching />
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
        <AiOperationWarning />
      </div>

      {/* 8. AI 활동 요약 */}
      <div
        id="section-ai-summary"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiWeeklySummary />
      </div>

      {/* 9. 플랜 관리 */}
      <div
        id="section-ai-plan"
        style={{ scrollMarginTop: '24px' }}
      >
        <AiPlanManagement />
      </div>
    </PageLayout>
  );
}
