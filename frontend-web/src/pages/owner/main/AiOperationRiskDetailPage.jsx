import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { ArrowLeft, Loader2, Lightbulb, Sparkles } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

import RiskSidebar from '../../../components/owner/ai/operation/RiskSidebar';
import RiskEnergySection from '../../../components/owner/ai/operation/RiskEnergySection';
import RiskSeasonalSection from '../../../components/owner/ai/operation/RiskSeasonalSection';
import RiskAnomalySection from '../../../components/owner/ai/operation/RiskAnomalySection';
import RiskSafetySection from '../../../components/owner/ai/operation/RiskSafetySection';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

const PageLayout = styled.div`
  max-width: 1140px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background-color: #f9fafb;
  min-height: 100vh;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  color: #6b7280;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 0;
  width: fit-content;

  &:hover {
    color: #111827;
  }
`;

const HeaderSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    h2 {
      font-size: 20px;
      font-weight: 700;
      margin: 0;
      color: #111827;
    }
  }

  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
  }
`;

const AiAnalysisBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #f0fdf4;
  color: #16a34a;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid #bbf7d0;
`;

const RiskSummaryBar = styled.div`
  background-color: ${(props) => (props.$isDanger ? '#fef2f2' : '#fffbe3')};
  border: 1px solid ${(props) => (props.$isDanger ? '#fecaca' : '#fef3c7')};
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
`;

const AlertDot = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: ${(props) => (props.$isDanger ? '#dc2626' : '#d97706')};
  font-weight: 700;
  font-size: 12px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    background-color: ${(props) => (props.$isDanger ? '#dc2626' : '#d97706')};
    border-radius: 50%;
  }
`;

const MainContent = styled.div`
  display: flex;
  gap: 20px;
  align-items: flex-start;

  @media (max-width: 1024px) {
    flex-direction: column;
  }
`;

const LeftContainer = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
`;

export default function AiOperationRiskDetailPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  // 모달 상태 제어
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [modalErrorMessage, setModalErrorMessage] = useState('');

  const fetchDetailData = async () => {
    try {
      setLoading(true);
      const res = await aiManagerApi.getRiskEarlyInfoDetail();
      if (res.data?.success) {
        setData(res.data.data);
      }
    } catch (err) {
      const status = err.response?.status;
      const errorData = err.response?.data?.error;

      // 403 권한 없음 또는 AI_001 에러 코드 감지 시 모달 띄움
      if (status === 403 || errorData?.code === 'AI_001') {
        setModalErrorMessage(
          errorData?.message ||
            '현재 플랜에서 사용할 수 없는 기능입니다. 플랜 업그레이드가 필요합니다.',
        );
        setIsUpgradeModalOpen(true);
      } else {
        console.error('운영 위험 상세 데이터 조회 실패:', err);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDetailData();
  }, []);

  if (loading) {
    return (
      <PageLayout>
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            padding: '100px',
          }}
        >
          <Loader2
            size={32}
            className="animate-spin"
            color="#9ca3af"
          />
        </div>
      </PageLayout>
    );
  }

  const isDanger =
    data?.overallRiskLevel === 'DANGER' || data?.overallRiskLevel === 'WARNING';

  return (
    <PageLayout>
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      <HeaderSection>
        <div className="title-row">
          <Lightbulb
            size={20}
            color="#f59e0b"
          />
          <h2>운영 위험 조기경보 상세</h2>
          <AiAnalysisBadge>
            <Sparkles size={12} /> AI 분석 완료
          </AiAnalysisBadge>
        </div>
        <p>
          산업부 공공데이터로 동네 에너지 경기·계절 급증·이상 변화·안전 리스크를
          미리 감지해요.
        </p>
      </HeaderSection>

      {data && (
        <>
          <RiskSummaryBar $isDanger={isDanger}>
            <AlertDot $isDanger={isDanger}>
              {data?.overallRiskLevel || '주의'}
            </AlertDot>
            <span style={{ fontWeight: 600, color: '#374151' }}>
              {data?.notice ||
                '종합 위험 신호 상태를 확인하고 사전에 대비하세요.'}
            </span>
          </RiskSummaryBar>

          <MainContent>
            <LeftContainer>
              <RiskEnergySection data={data} />
              <RiskSeasonalSection data={data} />
              <RiskAnomalySection data={data} />
              <RiskSafetySection data={data} />
            </LeftContainer>

            <RiskSidebar data={data} />
          </MainContent>
        </>
      )}

      {/* Pro 플랜 업그레이드 안내 모달 */}
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => {
          setIsUpgradeModalOpen(false);
          navigate(-1); // 권한이 없어 조회가 불가능하므로 이전 페이지로 백
        }}
        errorMessage={modalErrorMessage}
      />
    </PageLayout>
  );
}
