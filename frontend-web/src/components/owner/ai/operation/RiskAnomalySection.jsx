import React from 'react';
import styled from 'styled-components';
import { Activity, Sparkles } from 'lucide-react';

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

const GaugeContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 0;
`;

const GaugeHeader = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #6b7280;
`;

const GaugeTrack = styled.div`
  position: relative;
  height: 20px;
  background-color: #f3f4f6;
  border-radius: 10px;
`;

const NormalRange = styled.div`
  position: absolute;
  left: 30%;
  width: 45%;
  height: 100%;
  background-color: #86efac;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #166534;
`;

const Pointer = styled.div`
  position: absolute;
  left: 60%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background-color: #1f2937;
  border-radius: 50%;
  border: 2px solid #ffffff;
`;

const GaugeFooter = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #9ca3af;
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

export default function RiskAnomalySection({ data }) {
  const level = data?.activityAnomalyLevel || 'NORMAL';

  return (
    <SectionCard>
      <SectionHeader>
        <TitleArea>
          <h3>
            <Activity
              size={16}
              color="#16a34a"
            />{' '}
            업종 활동 이상 변화 감지
          </h3>
          <span className="source">
            출처: 한국전력공사·전력거래소 평년 패턴 대비 분석
          </span>
        </TitleArea>
        <Badge $level={level}>{getLevelText(level)}</Badge>
      </SectionHeader>

      <GaugeContainer>
        <GaugeHeader>
          <span>평년 패턴 대비 현재 위치</span>
          <span style={{ color: '#111827', fontWeight: 700 }}>현재 650kWh</span>
        </GaugeHeader>
        <GaugeTrack>
          <NormalRange>평년 정상 범위</NormalRange>
          <Pointer />
        </GaugeTrack>
        <GaugeFooter>
          <span>450kWh</span>
          <span style={{ color: '#16a34a', fontWeight: 600 }}>
            정상 580~720kWh
          </span>
          <span>850kWh</span>
        </GaugeFooter>
      </GaugeContainer>

      <AiBox>
        <Sparkles
          size={16}
          color="#16a34a"
          style={{ flexShrink: 0, marginTop: '2px' }}
        />
        <div>
          <strong>AI 판단</strong>
          <br />
          {data?.activityAnomaly ||
            '현재 사용 패턴은 평년 범위 안에 있어요. 누전·과부하 같은 갑작스러운 이상 신호는 감지되지 않았어요.'}
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
        <CheckItem>
          <input
            type="checkbox"
            defaultChecked
          />{' '}
          별도 조치 불필요 — 모니터링 유지
        </CheckItem>
      </div>
    </SectionCard>
  );
}
