import React from 'react';
import styled from 'styled-components';
import { ShieldAlert, Sparkles } from 'lucide-react';

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

const KeyWordBadge = styled.span`
  background-color: #f3f4f6;
  color: #4b5563;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-right: 4px;
`;

const getLevelText = (level) => {
  if (level === 'DANGER') return '경보';
  if (level === 'WARNING') return '주의';
  return '정상';
};

export default function RiskSafetySection({ data }) {
  const level = data?.safetyCheckLevel || 'WARNING';
  const insight = data?.gasSafetyInsight;

  const defaultActions = [
    '영업 전 가스 밸브·호스 점검',
    '주방 환기팬 작동·필터 상태 확인',
    '화기 주변 정리 및 소화기 점검',
  ];

  const actionsToRender = insight?.recommendedActions?.length
    ? insight.recommendedActions
    : defaultActions;

  return (
    <SectionCard>
      <SectionHeader>
        <TitleArea>
          <h3>
            <ShieldAlert
              size={16}
              color="#d97706"
            />{' '}
            안전 리스크 체크
          </h3>
          <span className="source">
            출처: 한국가스안전공사 가스사고 현황 + 이음 리뷰·신고 키워드
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
          {data?.safetyCheck ||
            "최근 리뷰에서 '냄새·연기' 표현이 반복돼요. 가스를 쓰는 업종에서는 환기·밸브 상태를 우선 확인하시길 권장해요."}
          {insight?.detectedKeywords?.length > 0 && (
            <div style={{ marginTop: '6px' }}>
              <span style={{ fontSize: '11px', color: '#6b7280' }}>
                감지된 키워드:{' '}
              </span>
              {insight.detectedKeywords.map((kw, i) => (
                <KeyWordBadge key={i}>#{kw}</KeyWordBadge>
              ))}
            </div>
          )}
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
          {actionsToRender.map((action, idx) => (
            <CheckItem key={idx}>
              <input type="checkbox" /> {action}
            </CheckItem>
          ))}
        </ChecklistGroup>
      </div>
    </SectionCard>
  );
}
