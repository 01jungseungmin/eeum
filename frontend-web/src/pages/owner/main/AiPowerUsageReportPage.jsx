import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import {
  ArrowLeft,
  Sparkles,
  Download,
  Check,
  FileText,
  Loader2,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

/* 기존 Styled Components 유지 */
const PageLayout = styled.div`
  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 24px;
  background-color: #f9fafb;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 20px;
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
      font-size: 22px;
      font-weight: 700;
      color: #111827;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
  }
`;

const AiBadge = styled.span`
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

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;
  @media (max-width: 868px) {
    grid-template-columns: 1fr;
  }
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const Card = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  .sub {
    font-size: 11px;
    color: #9ca3af;
  }
`;

const ChartContainer = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  height: 160px;
  padding: 20px 10px 0 10px;
  border-bottom: 1px solid #f3f4f6;
`;

const BarColumn = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
  .value {
    font-size: 11px;
    font-weight: 700;
    color: ${(props) => (props.$isHighlight ? '#ea580c' : '#9ca3af')};
  }
  .bar {
    width: 28px;
    height: ${(props) => props.$height}px;
    background-color: ${(props) =>
      props.$isHighlight ? '#f97316' : '#86efac'};
    border-radius: 4px 4px 0 0;
  }
  .month {
    font-size: 11px;
    color: #6b7280;
  }
`;

const HighLightTag = styled.span`
  background-color: #fff7ed;
  color: #ea580c;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #ffedd5;
`;

const EquipmentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const EquipmentRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  .top {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    font-weight: 600;
    color: #374151;
  }
  .track {
    height: 8px;
    background-color: #f3f4f6;
    border-radius: 4px;
    overflow: hidden;
  }
  .fill {
    height: 100%;
    background-color: ${(props) => props.$color};
    border-radius: 4px;
  }
`;

const DiagnosisList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const DiagnosisItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  .icon {
    margin-top: 2px;
    color: #10b981;
    flex-shrink: 0;
  }
`;

const RightSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const InfoCard = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  .title {
    font-size: 12px;
    font-weight: 700;
    color: #9ca3af;
  }
`;

const InfoRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  .label {
    color: #6b7280;
  }
  .value {
    font-weight: 700;
    color: #111827;
    &.orange {
      color: #ea580c;
    }
  }
`;

const ActionButton = styled.button`
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: ${(props) => (props.$primary ? 'none' : '1px solid #e5e7eb')};
  background-color: ${(props) => (props.$primary ? '#10b981' : '#ffffff')};
  color: ${(props) => (props.$primary ? '#ffffff' : '#374151')};
  &:hover {
    opacity: 0.9;
  }
`;

export default function AiPowerUsageReportPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [report, setReport] = useState(null);

  // 모달 상태 제어
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [modalErrorMessage, setModalErrorMessage] = useState('');

  useEffect(() => {
    const fetchReport = async () => {
      try {
        setLoading(true);
        const res = await aiManagerApi.getPowerUsageReport();
        if (res.data?.success) {
          setReport(res.data.data);
        }
      } catch (err) {
        const status = err.response?.status;
        const errorData = err.response?.data?.error;

        // 403 권한 없음 또는 AI_001 코드
        if (status === 403 || errorData?.code === 'AI_001') {
          setModalErrorMessage(
            errorData?.message ||
              '현재 플랜에서 사용할 수 없는 기능입니다. 플랜 업그레이드가 필요합니다.',
          );
          setIsUpgradeModalOpen(true);
        } else {
          console.error('리포트 조회 실패:', err);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchReport();
  }, []);

  const monthlyUsages = report?.monthlyUsages || [];
  const equipmentShares = report?.equipmentShares || [];
  const eqColors = ['#2563eb', '#10b981', '#10b981', '#f59e0b'];

  return (
    <PageLayout>
      <BackButton onClick={() => navigate('/ai-manager/operation-risk')}>
        <ArrowLeft size={16} /> 운영 위험 조기경보로 돌아가기
      </BackButton>

      <HeaderSection>
        <div className="title-row">
          <h2>
            <FileText
              size={22}
              color="#f59e0b"
            />{' '}
            전력 사용 상세 리포트
          </h2>
          <AiBadge>
            <Sparkles size={12} /> AI 분석 완료
          </AiBadge>
        </div>
        <p>최근 6개월 전력 사용을 항목별로 분석한 리포트예요.</p>
      </HeaderSection>

      {loading ? (
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
            color="#10b981"
          />
        </div>
      ) : report ? (
        <MainGrid>
          <LeftSection>
            <Card>
              <CardHeader>
                <h3>월별 사용량</h3>
                <span className="sub">kWh · 최근 6개월</span>
              </CardHeader>

              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <HighLightTag>⚡ 여름철 급증</HighLightTag>
              </div>

              <ChartContainer>
                {monthlyUsages.map((item, idx) => {
                  const isHighlight = idx === monthlyUsages.length - 1;
                  const heightPx = Math.min(
                    120,
                    Math.max(30, (item.kwh / 900) * 120),
                  );
                  const monthText = `${parseInt(item.yearMonth.split('-')[1])}월`;

                  return (
                    <BarColumn
                      key={idx}
                      $height={heightPx}
                      $isHighlight={isHighlight}
                    >
                      <span className="value">{item.kwh}</span>
                      <div className="bar" />
                      <span className="month">{monthText}</span>
                    </BarColumn>
                  );
                })}
              </ChartContainer>
            </Card>

            <Card>
              <CardHeader>
                <h3>설비별 사용 비중</h3>
                <span className="sub">8월 기준</span>
              </CardHeader>

              <EquipmentList>
                {equipmentShares.map((eq, idx) => (
                  <EquipmentRow
                    key={idx}
                    $color={eqColors[idx % eqColors.length]}
                  >
                    <div className="top">
                      <span>{eq.name}</span>
                      <span>{eq.ratio}%</span>
                    </div>
                    <div className="track">
                      <div
                        className="fill"
                        style={{ width: `${eq.ratio}%` }}
                      />
                    </div>
                  </EquipmentRow>
                ))}
              </EquipmentList>
            </Card>

            <Card>
              <CardHeader>
                <h3>핵심 진단</h3>
              </CardHeader>

              <DiagnosisList>
                {report.keyDiagnosis?.map((diag, idx) => (
                  <DiagnosisItem key={idx}>
                    <Check
                      size={16}
                      className="icon"
                    />
                    <span>{diag}</span>
                  </DiagnosisItem>
                ))}
              </DiagnosisList>
            </Card>
          </LeftSection>

          <RightSection>
            <InfoCard>
              <div className="title">리포트 정보</div>
              <InfoRow>
                <span className="label">분석 기간</span>
                <span className="value">{report.analysisPeriod}</span>
              </InfoRow>
              <InfoRow>
                <span className="label">업종 비교</span>
                <span className="value">{report.industryComparison}</span>
              </InfoRow>
              <InfoRow>
                <span className="label">추정 절감</span>
                <span className="value orange">
                  월 {(report.estimatedSavingAmount / 10000).toFixed(1)}만원
                </span>
              </InfoRow>
            </InfoCard>

            <ActionButton
              $primary
              onClick={() => alert('리포트 다운로드가 시작됩니다.')}
            >
              <Download size={16} /> 리포트 내려받기
            </ActionButton>

            <ActionButton onClick={() => navigate('/ai-manager/saving-plan')}>
              절감 계획 만들기
            </ActionButton>
          </RightSection>
        </MainGrid>
      ) : null}

      {/* 구독 업그레이드 안내 모달 */}
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => {
          setIsUpgradeModalOpen(false);
          navigate(-1);
        }}
        errorMessage={modalErrorMessage}
      />
    </PageLayout>
  );
}
