import React from 'react';
import styled from 'styled-components';
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
} from 'lucide-react';

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

const AlertBanner = styled.div`
  background-color: #fffbe3;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const WarningBadge = styled.span`
  background-color: #fee2e2;
  color: #dc2626;
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
    background-color: #dc2626;
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
  justify-content: space-between;
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

const MoreButton = styled.button`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;

  &:hover {
    background-color: #f9fafb;
  }
`;

export default function AiOperationWarning() {
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
      </Header>

      {/* 경보 상태 배너 */}
      <AlertBanner>
        <WarningBadge>경보</WarningBadge>
        <div>
          종합 위험 신호 — 신호 4개 중 <strong>경보 1 · 주의 2 · 정상 1</strong>{' '}
          감지
        </div>
      </AlertBanner>

      {/* 2x2 카드 그리드 */}
      <GridContainer>
        {/* 1. 동네 에너지 경기 신호 */}
        <WarningCard
          $bgColor="#fffdf5"
          $borderColor="#fef3c7"
        >
          <CardHeader>
            <CardTitle>
              <TrendingDown
                size={18}
                color="#d97706"
              />{' '}
              동네 에너지 경기 신호
            </CardTitle>
            <StatusTag
              $bgColor="#fef3c7"
              $color="#d97706"
            >
              주의
            </StatusTag>
          </CardHeader>
          <CardDesc>
            우리 동네 음식점업, 최근 3개월 에너지 사용 -8%. 상권 활동 위축
            가능성.
          </CardDesc>
          <InfoBox
            $badgeBg="#e0f2fe"
            $badgeColor="#0284c7"
          >
            <span className="label">정밀측정</span>
            <span>
              우리 가게: 국토교통부 건물에너지정보(지번 단위) · 동네 평균:
              한국전력공사 법정동별 전력사용량
            </span>
          </InfoBox>
          <SourceText>
            출처: 한국전력거래소 행정구역별 에너지사용량 통합데이터
          </SourceText>
        </WarningCard>

        {/* 2. 계절·시기 선제 알림 */}
        <WarningCard
          $bgColor="#fff5f5"
          $borderColor="#fee2e2"
        >
          <CardHeader>
            <CardTitle>
              <Calendar
                size={18}
                color="#dc2626"
              />{' '}
              계절·시기 선제 알림
            </CardTitle>
            <StatusTag
              $bgColor="#fee2e2"
              $color="#dc2626"
            >
              경보
            </StatusTag>
          </CardHeader>
          <CardDesc>
            다음 달부터 우리 업종 전력 사용 급증 시기. 지난 3년 평균 +32%.
          </CardDesc>
          <InfoBox
            $badgeBg="#fef3c7"
            $badgeColor="#d97706"
          >
            <span className="label">입력값</span>
            <span>
              우리 가게: 사장님 입력값(전기요금 고지서 기준) · 동네 평균:
              한국전력공사 법정동별 전력사용량
            </span>
          </InfoBox>
          <SourceText>
            출처: 한국전력공사 산업분류별 법정동별 전력사용량(월별)
          </SourceText>
        </WarningCard>

        {/* 3. 업종 활동 이상 변화 감지 */}
        <WarningCard
          $bgColor="#f0fdf4"
          $borderColor="#dcfce7"
        >
          <CardHeader>
            <CardTitle>
              <Activity
                size={18}
                color="#16a34a"
              />{' '}
              업종 활동 이상 변화 감지
            </CardTitle>
            <StatusTag
              $bgColor="#dcfce7"
              $color="#16a34a"
            >
              정상
            </StatusTag>
          </CardHeader>
          <CardDesc>현재 사용 패턴은 평년 범위 내. 특이 신호 없음.</CardDesc>
          <InfoBox
            $badgeBg="#f3f4f6"
            $badgeColor="#4b5563"
          >
            <span className="label">동네평균만</span>
            <span>
              개별 가게 비교 데이터 없음 · 동네 평균: 한국전력공사 법정동별
              전력사용량 추세만 제공
            </span>
          </InfoBox>
          <SourceText>
            출처: 한국전력공사·전력거래소 평년 패턴 대비 분석
          </SourceText>
        </WarningCard>

        {/* 4. 안전 리스크 체크 */}
        <WarningCard
          $bgColor="#fffdf5"
          $borderColor="#fef3c7"
        >
          <CardHeader>
            <CardTitle>
              <ShieldAlert
                size={18}
                color="#d97706"
              />{' '}
              안전 리스크 체크
            </CardTitle>
            <StatusTag
              $bgColor="#fef3c7"
              $color="#d97706"
            >
              주의
            </StatusTag>
          </CardHeader>
          <CardDesc>
            최근 리뷰에서 '냄새·연기' 표현 반복. 가스 사용 업종 환기·밸브 점검
            권장.
          </CardDesc>
          <SourceText>
            출처: 한국가스안전공사 가스사고 현황 + 이음 리뷰·신고 키워드
          </SourceText>
        </WarningCard>
      </GridContainer>

      {/* AI 점장 판단 요약 */}
      <AiSummaryBox>
        <Sparkles
          size={18}
          color="#16a34a"
          style={{ marginTop: '2px' }}
        />
        <AiSummaryContent>
          <h5>AI 점장 판단</h5>
          <p>
            다음 달 <strong>전력 사용 급증 시기</strong>가 다가오고, 리뷰에서{' '}
            <strong>냄새·연기</strong> 신호가 반복돼요. 지금 환기·밸브 점검과
            냉방 효율 준비를 함께 해두시길 권장해요.
          </p>
        </AiSummaryContent>
      </AiSummaryBox>

      {/* 하단 푸터 */}
      <Footer>
        <FooterText>
          <Info size={14} /> 산업부 공공데이터는 연 단위 갱신으로, 실시간 측정이
          아닌 계절·구조적 위험 패턴 분석에 활용됩니다.
        </FooterText>
        <MoreButton>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Footer>
    </CardContainer>
  );
}
