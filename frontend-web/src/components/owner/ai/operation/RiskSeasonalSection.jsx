import React from 'react';
import styled from 'styled-components';
import { Calendar, Sparkles } from 'lucide-react';

const SectionCard = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const SectionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const TitleArea = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;

  h3 {
    font-size: 15px;
    font-weight: 700;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 6px;
    color: #111827;
  }

  span.source {
    font-size: 11px;
    color: #9ca3af;
  }
`;

const Badge = styled.span`
  background-color: ${(props) =>
    props.$level === 'DANGER'
      ? '#fee2e2'
      : props.$level === 'WARNING'
        ? '#fef3c7'
        : '#dcfce7'};
  color: ${(props) =>
    props.$level === 'DANGER'
      ? '#dc2626'
      : props.$level === 'WARNING'
        ? '#d97706'
        : '#16a34a'};
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 4px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    background-color: ${(props) =>
      props.$level === 'DANGER'
        ? '#dc2626'
        : props.$level === 'WARNING'
          ? '#d97706'
          : '#16a34a'};
    border-radius: 50%;
  }
`;

const ChartContainer = styled.div`
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const ChartTitle = styled.div`
  font-size: 11px;
  color: #6b7280;
  font-weight: 600;
`;

const ChartBars = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  height: 90px;
  padding: 0 10px;
  border-bottom: 1px solid #f3f4f6;
`;

const BarGroup = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  .bars {
    display: flex;
    align-items: flex-end;
    gap: 3px;
    height: 70px;
  }

  span.month {
    font-size: 10px;
    color: #9ca3af;
  }
`;

const Bar = styled.div`
  width: 10px;
  height: ${(props) => props.$height}%;
  background-color: ${(props) => props.$color};
  border-radius: 2px 2px 0 0;
`;

const ChartLegend = styled.div`
  display: flex;
  gap: 12px;
  font-size: 10px;
  color: #6b7280;

  .item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
`;

const LegendColor = styled.span`
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background-color: ${(props) => props.$bg};
`;

const AiBox = styled.div`
  background-color: #f0fdf4;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  gap: 10px;
  font-size: 12.5px;
  color: #374151;
  line-height: 1.5;
`;

const ChecklistGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const CheckItem = styled.label`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 12.5px;
  color: #374151;
  cursor: pointer;

  input {
    width: 16px;
    height: 16px;
    accent-color: #10b981;
    border-radius: 4px;
  }
`;

const getLevelText = (level) => {
  if (level === 'DANGER') return '경보';
  if (level === 'WARNING') return '주의';
  return '정상';
};

export default function RiskSeasonalSection({ data }) {
  const level = data?.seasonalAlertLevel || 'DANGER';

  const chartData = [
    { month: '3월', avg: 35, my: 40 },
    { month: '4월', avg: 40, my: 45 },
    { month: '5월', avg: 50, my: 55 },
    { month: '6월', avg: 65, my: 70 },
    { month: '7월', avg: 75, my: 85 },
    { month: '8월', avg: 80, my: 95 },
  ];

  return (
    <SectionCard>
      <SectionHeader>
        <TitleArea>
          <h3>
            <Calendar
              size={16}
              color="#dc2626"
            />{' '}
            계절·시기 선제 알림
          </h3>
          <span className="source">
            출처: 한국전력공사 산업분류별 법정동별 전력사용량(월별)
          </span>
        </TitleArea>
        <Badge $level={level}>{getLevelText(level)}</Badge>
      </SectionHeader>

      <ChartContainer>
        <ChartTitle>
          최근 6개월 추세 - 한국전력공사 법정동별 전력사용량 비교
        </ChartTitle>
        <ChartBars>
          {chartData.map((item, idx) => (
            <BarGroup key={idx}>
              <div className="bars">
                <Bar
                  $height={item.avg}
                  $color="#86efac"
                />
                <Bar
                  $height={item.my}
                  $color="#f97316"
                />
              </div>
              <span className="month">{item.month}</span>
            </BarGroup>
          ))}
        </ChartBars>
        <ChartLegend>
          <div className="item">
            <LegendColor $bg="#86efac" /> 동종 업종 평균 (공공데이터)
          </div>
          <div className="item">
            <LegendColor $bg="#f97316" /> 우리 가게 입력값
          </div>
        </ChartLegend>
      </ChartContainer>

      <AiBox>
        <Sparkles
          size={16}
          color="#16a34a"
          style={{ flexShrink: 0, marginTop: '2px' }}
        />
        <div>
          <strong>AI 판단</strong>
          <br />
          {data?.seasonalAlert ||
            '다음 달부터 우리 업종은 냉방으로 전력 사용이 급증하는 시기예요. 지난 3년 평균 +32% 올랐어요. 지금 준비하면 요금 충격을 줄일 수 있어요.'}
        </div>
      </AiBox>

      <div>
        <div
          style={{
            fontSize: '11px',
            fontWeight: 600,
            color: '#9ca3af',
            marginBottom: '6px',
          }}
        >
          준비할 대응
        </div>
        <ChecklistGroup>
          <CheckItem>
            <input type="checkbox" /> 냉장·냉방 설비 사전 점검 예약
          </CheckItem>
          <CheckItem>
            <input type="checkbox" /> 계약전력 초과 여부 미리 확인
          </CheckItem>
          <CheckItem>
            <input type="checkbox" /> 필터 청소·고효율 기기 교체 검토
          </CheckItem>
        </ChecklistGroup>
      </div>
    </SectionCard>
  );
}
