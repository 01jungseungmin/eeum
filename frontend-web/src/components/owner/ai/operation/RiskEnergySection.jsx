import React from 'react';
import styled from 'styled-components';
import { TrendingDown, Sparkles } from 'lucide-react';

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

export default function RiskEnergySection({ data }) {
  const level = data?.energySignalLevel || 'WARNING';
  const defaultChecklist = [
    '고정비(임대·구독 서비스) 재점검',
    '재고 발주량 10~15% 보수적 조정',
    '한산한 시간대 조명·인력 운영 조정',
  ];
  const listToRender = data?.checklist?.length
    ? data.checklist
    : defaultChecklist;

  return (
    <SectionCard>
      <SectionHeader>
        <TitleArea>
          <h3>
            <TrendingDown
              size={16}
              color={level === 'DANGER' ? '#dc2626' : '#d97706'}
            />{' '}
            동네 에너지 경기 신호
          </h3>
          <span className="source">
            출처: 한국전력거래소 행정구역별 에너지사용량 통합데이터
          </span>
        </TitleArea>
        <Badge $level={level}>{getLevelText(level)}</Badge>
      </SectionHeader>

      <AiBox>
        <Sparkles
          size={16}
          color="#16a34a"
          style={{ flexShrink: 0, marginTop: '2px' }}
        />
        <div>
          <strong>AI 판단</strong>
          <br />
          {data?.energySignal ||
            '동네 음식점업 에너지 사용량이 3개월 연속 줄었어요. 상권 전체의 활동이 둔화되는 신호일 수 있어, 당분간 고정 비용을 보수적으로 관리하시길 권장해요.'}
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
          {listToRender.map((item, index) => (
            <CheckItem key={index}>
              <input type="checkbox" /> {item}
            </CheckItem>
          ))}
        </ChecklistGroup>
      </div>
    </SectionCard>
  );
}
