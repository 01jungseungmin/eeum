import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  Siren,
  ChevronRight,
  Crown,
  TrendingDown,
  Calendar,
  Activity,
  ShieldAlert,
  Sparkles,
  Info,
  Loader2,
} from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

const CardContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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
  background-color: #fef3c7;
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const TitleArea = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  h3 {
    font-size: 18px;
    font-weight: 700;
    margin: 0;
    color: #111827;
  }
`;

const BadgePro = styled.span`
  background-color: #f59e0b;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 2px;
`;

const SubText = styled.p`
  font-size: 12px;
  color: #9ca3af;
  margin: 4px 0 0;
`;

/* 상단 Header 우측으로 이동된 더보기 버튼 스타일 */
const HeaderMoreButton = styled.button`
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: #4b5563;
  }
`;

const AlertBanner = styled.div`
  background-color: ${(props) => props.$bgColor || '#fffbe3'};
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const WarningBadge = styled.span`
  background-color: ${(props) => props.$bgColor || '#fee2e2'};
  color: ${(props) => props.$textColor || '#dc2626'};
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 4px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: ${(props) => props.$textColor || '#dc2626'};
  }
`;

const GridContainer = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const WarningCard = styled.div`
  background-color: ${(props) => props.$bgColor || '#ffffff'};
  border: 1px solid ${(props) => props.$borderColor || '#e5e7eb'};
  border-radius: 14px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const CardTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
`;

const StatusTag = styled.span`
  font-size: 12px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 12px;
  background-color: ${(props) => props.$bgColor};
  color: ${(props) => props.$color};
  display: flex;
  align-items: center;
  gap: 4px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: ${(props) => props.$color};
  }
`;

const CardDesc = styled.p`
  font-size: 13px;
  color: #374151;
  margin: 0;
  line-height: 1.4;
`;

const InfoBox = styled.div`
  background-color: #ffffff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 11px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 6px;

  span.label {
    background-color: ${(props) => props.$badgeBg || '#e5e7eb'};
    color: ${(props) => props.$badgeColor || '#374151'};
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 10px;
    white-space: nowrap;
  }
`;

const SourceText = styled.span`
  font-size: 11px;
  color: #9ca3af;
`;

const AiSummaryBox = styled.div`
  background-color: #f0fdf4;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
`;

const AiSummaryContent = styled.div`
  h5 {
    font-size: 12px;
    font-weight: 700;
    color: #166534;
    margin: 0 0 4px 0;
  }
  p {
    font-size: 13px;
    color: #1f2937;
    margin: 0;
    line-height: 1.5;

    strong {
      font-weight: 700;
    }
  }
`;

const Footer = styled.div`
  display: flex;
  align-items: center;
  margin-top: 4px;
`;

const FooterText = styled.span`
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
`;

const LoadingBox = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px;
  color: #9ca3af;
`;

// 위험도 레벨 매핑 Helper
const getLevelStyle = (level) => {
  switch (level) {
    case 'CRITICAL':
      return {
        label: '경보',
        badgeBg: '#fee2e2',
        badgeColor: '#dc2626',
        cardBg: '#fff5f5',
        cardBorder: '#fee2e2',
        iconColor: '#dc2626',
      };
    case 'WARNING':
      return {
        label: '주의',
        badgeBg: '#fef3c7',
        badgeColor: '#d97706',
        cardBg: '#fffdf5',
        cardBorder: '#fef3c7',
        iconColor: '#d97706',
      };
    case 'NORMAL':
    default:
      return {
        label: '정상',
        badgeBg: '#dcfce7',
        badgeColor: '#16a34a',
        cardBg: '#f0fdf4',
        cardBorder: '#dcfce7',
        iconColor: '#16a34a',
      };
  }
};

export default function AiOperationWarning() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await aiManagerApi.getRiskEarlyInfo();
        if (res.data?.success && res.data?.data) {
          setData(res.data.data);
        }
      } catch (error) {
        console.error('운영 위험 조기경보 데이터 로딩 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <CardContainer id="section-ai-warning">
        <LoadingBox>
          <Loader2
            size={24}
            className="animate-spin"
          />
        </LoadingBox>
      </CardContainer>
    );
  }

  // 레벨 스타일 할당
  const overallStyle = getLevelStyle(data?.overallRiskLevel);
  const energyStyle = getLevelStyle(data?.energySignalLevel);
  const seasonalStyle = getLevelStyle(data?.seasonalAlertLevel);
  const activityStyle = getLevelStyle(data?.activityAnomalyLevel);
  const safetyStyle = getLevelStyle(data?.safetyCheckLevel);

  // 사장님 데이터 소스 정보 추출
  const ownerSource = data?.dataSources?.find(
    (s) => s.sourceType === 'OWNER_INPUT',
  );

  return (
    <CardContainer id="section-ai-warning">
      {/* 헤더 */}
      <Header>
        <HeaderLeft>
          <IconBox>
            <Siren size={20} />
          </IconBox>
          <div>
            <TitleArea>
              <h3>산업부 데이터 기반 운영 위험 조기경보</h3>
              <BadgePro>
                <Crown size={12} /> Pro
              </BadgePro>
            </TitleArea>
            <SubText>
              한국전력공사·전력거래소·한국가스안전공사 공공데이터 기반
            </SubText>
          </div>
        </HeaderLeft>

        {/* 상단 우측 '더보기 >' 버튼 */}
        <HeaderMoreButton
          onClick={() => navigate('/ai-manager/operation-risk')}
        >
          더보기 <ChevronRight size={16} />
        </HeaderMoreButton>
      </Header>

      {/* 경보 상태 배너 */}
      <AlertBanner $bgColor={overallStyle.cardBg}>
        <WarningBadge
          $bgColor={overallStyle.badgeBg}
          $textColor={overallStyle.badgeColor}
        >
          {overallStyle.label}
        </WarningBadge>
        <div>
          종합 위험 신호 —{' '}
          <strong>
            {data?.notice || '실시간 상태 분석이 연동되었습니다.'}
          </strong>
        </div>
      </AlertBanner>

      {/* 2x2 카드 그리드 */}
      <GridContainer>
        {/* 1. 동네 에너지 경기 신호 */}
        <WarningCard
          $bgColor={energyStyle.cardBg}
          $borderColor={energyStyle.cardBorder}
        >
          <CardHeader>
            <CardTitle>
              <TrendingDown
                size={18}
                color={energyStyle.iconColor}
              />{' '}
              동네 에너지 경기 신호
            </CardTitle>
            <StatusTag
              $bgColor={energyStyle.badgeBg}
              $color={energyStyle.badgeColor}
            >
              {energyStyle.label}
            </StatusTag>
          </CardHeader>
          <CardDesc>
            {data?.energySignal || '데이터를 불러오는 중입니다.'}
          </CardDesc>
          {ownerSource && (
            <InfoBox
              $badgeBg="#e0f2fe"
              $badgeColor="#0284c7"
            >
              <span className="label">{ownerSource.sourceLabel}</span>
              <span>{ownerSource.description}</span>
            </InfoBox>
          )}
          <SourceText>
            출처: 한국전력거래소 행정구역별 에너지사용량 통합데이터
          </SourceText>
        </WarningCard>

        {/* 2. 계절·시기 선제 알림 */}
        <WarningCard
          $bgColor={seasonalStyle.cardBg}
          $borderColor={seasonalStyle.cardBorder}
        >
          <CardHeader>
            <CardTitle>
              <Calendar
                size={18}
                color={seasonalStyle.iconColor}
              />{' '}
              계절·시기 선제 알림
            </CardTitle>
            <StatusTag
              $bgColor={seasonalStyle.badgeBg}
              $color={seasonalStyle.badgeColor}
            >
              {seasonalStyle.label}
            </StatusTag>
          </CardHeader>
          <CardDesc>
            {data?.seasonalAlert || '데이터를 불러오는 중입니다.'}
          </CardDesc>
          <SourceText>
            출처: 한국전력공사 산업분류별 법정동별 전력사용량(월별)
          </SourceText>
        </WarningCard>

        {/* 3. 업종 활동 이상 변화 감지 */}
        <WarningCard
          $bgColor={activityStyle.cardBg}
          $borderColor={activityStyle.cardBorder}
        >
          <CardHeader>
            <CardTitle>
              <Activity
                size={18}
                color={activityStyle.iconColor}
              />{' '}
              업종 활동 이상 변화 감지
            </CardTitle>
            <StatusTag
              $bgColor={activityStyle.badgeBg}
              $color={activityStyle.badgeColor}
            >
              {activityStyle.label}
            </StatusTag>
          </CardHeader>
          <CardDesc>
            {data?.activityAnomaly || '현재 사용 패턴은 평년 범위 내입니다.'}
          </CardDesc>
          <SourceText>
            출처: 한국전력공사·전력거래소 평년 패턴 대비 분석
          </SourceText>
        </WarningCard>

        {/* 4. 안전 리스크 체크 */}
        <WarningCard
          $bgColor={safetyStyle.cardBg}
          $borderColor={safetyStyle.cardBorder}
        >
          <CardHeader>
            <CardTitle>
              <ShieldAlert
                size={18}
                color={safetyStyle.iconColor}
              />{' '}
              안전 리스크 체크
            </CardTitle>
            <StatusTag
              $bgColor={safetyStyle.badgeBg}
              $color={safetyStyle.badgeColor}
            >
              {safetyStyle.label}
            </StatusTag>
          </CardHeader>
          <CardDesc>
            {data?.safetyCheck || '안전 관련 특이사항이 없습니다.'}
          </CardDesc>
          <SourceText>
            출처: 한국가스안전공사 가스사고 현황 + 이음 리뷰·신고 키워드
          </SourceText>
        </WarningCard>
      </GridContainer>

      {/* AI 점장 판단 요약 */}
      {data?.aiJudgement && (
        <AiSummaryBox>
          <Sparkles
            size={18}
            color="#16a34a"
            style={{ marginTop: '2px', flexShrink: 0 }}
          />
          <AiSummaryContent>
            <h5>AI 점장 판단</h5>
            <p>{data.aiJudgement}</p>
          </AiSummaryContent>
        </AiSummaryBox>
      )}

      {/* 하단 푸터 */}
      <Footer>
        <FooterText>
          <Info size={14} /> 산업부 공공데이터는 연 단위 갱신으로, 실시간 측정이
          아닌 계절·구조적 위험 패턴 분석에 활용됩니다.
        </FooterText>
      </Footer>
    </CardContainer>
  );
}
