import React from 'react';
import styled from 'styled-components';
import {
  TrendingUp,
  ChevronRight,
  Sparkles,
  Tag,
  Lightbulb,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

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

const MoreButton = styled.button`
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  &:hover {
    color: #374151;
  }
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
`;

const StatCard = styled.div`
  background-color: #fafafa;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 16px;
`;

const StatTitle = styled.span`
  font-size: 12px;
  color: #6b7280;
  display: block;
  margin-bottom: 8px;
`;

const StatValue = styled.div`
  font-size: 20px;
  font-weight: 800;
  color: #111827;
  display: flex;
  align-items: baseline;
  gap: 6px;

  .old {
    font-size: 15px;
    color: #9ca3af;
    text-decoration: line-through;
    font-weight: 500;
  }
  .arrow {
    font-size: 14px;
    color: #9ca3af;
    font-weight: 400;
  }
  .unit {
    font-size: 14px;
    font-weight: 500;
    color: #4b5563;
  }
`;

const AiSummaryBox = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
`;

const AiSummaryIcon = styled.div`
  color: #16a34a;
  margin-top: 2px;
`;

const AiSummaryContent = styled.div`
  h5 {
    font-size: 12px;
    font-weight: 700;
    color: #166534;
    margin: 0 0 2px 0;
  }
  p {
    font-size: 13px;
    color: #374151;
    margin: 0;
    line-height: 1.4;
  }
`;

const TipBox = styled.div`
  background-color: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    font-weight: 700;
    color: #d97706;
  }
`;

const CreateEventButton = styled.button`
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

export default function AiEventPerformance({ data }) {
  const navigate = useNavigate();

  // 소수점 수치 % 변환 계산 (예: 0.114 -> 11.4%)
  const conversionRate =
    data?.orderConversionRate != null
      ? (data.orderConversionRate * 100).toFixed(1)
      : '0';

  const newCustomerRatio =
    data?.newCustomerRatio != null
      ? (data.newCustomerRatio * 100).toFixed(0)
      : '0';

  return (
    <CardContainer id="section-ai-event">
      <Header>
        <HeaderLeft>
          <IconBox>
            <TrendingUp size={20} />
          </IconBox>
          <TitleArea>
            <h3>이벤트 성과 매니저</h3>
            <p>지난 이벤트 결과 분석</p>
          </TitleArea>
        </HeaderLeft>
        <MoreButton onClick={() => navigate('/ai-manager/event')}>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      {/* 수치 지표 그리드 */}
      <StatsGrid>
        <StatCard>
          <StatTitle>상품 조회수</StatTitle>
          <StatValue>
            {data?.productViewCount?.toLocaleString() ?? 0}{' '}
            <span className="unit">회</span>
          </StatValue>
        </StatCard>

        <StatCard>
          <StatTitle>주문 전환율</StatTitle>
          <StatValue>
            {conversionRate} <span className="unit">%</span>
          </StatValue>
        </StatCard>

        <StatCard>
          <StatTitle>신규 고객 비중</StatTitle>
          <StatValue>
            {newCustomerRatio} <span className="unit">%</span>
          </StatValue>
        </StatCard>

        <StatCard>
          <StatTitle>단골 재주문</StatTitle>
          <StatValue>
            {data?.regularReorderCount?.toLocaleString() ?? 0}{' '}
            <span className="unit">건</span>
          </StatValue>
        </StatCard>
      </StatsGrid>

      {/* AI 요약 */}
      <AiSummaryBox>
        <AiSummaryIcon>
          <Sparkles size={18} />
        </AiSummaryIcon>
        <AiSummaryContent>
          <h5>AI 요약</h5>
          <p>
            {data?.aiSummary ||
              data?.emptyMessage ||
              '이벤트 성과 데이터 수집 중입니다.'}
          </p>
        </AiSummaryContent>
      </AiSummaryBox>

      {/* 추천 액션 / 팁 박스 */}
      {data?.nextEventRecommendation?.reason && (
        <TipBox>
          <Lightbulb
            size={16}
            color="#d97706"
          />
          <div>{data.nextEventRecommendation.reason}</div>
        </TipBox>
      )}

      {/* 다음 이벤트 만들기 버튼 */}
      <CreateEventButton
        onClick={() =>
          navigate('/ai-manager/event/create', {
            state: { recommendation: data?.nextEventRecommendation || null },
          })
        }
      >
        <Tag size={16} /> 다음 이벤트 만들기
      </CreateEventButton>
    </CardContainer>
  );
}
