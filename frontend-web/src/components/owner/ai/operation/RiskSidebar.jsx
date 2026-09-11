import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { ShieldCheck, FileText, Lock, Check } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const SidebarContainer = styled.div`
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 300px;
  flex-shrink: 0;

  @media (max-width: 1024px) {
    width: 100%;
    position: static;
  }
`;

const Card = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px;
`;

const Title = styled.h4`
  font-size: 13px;
  font-weight: 700;
  color: #374151;
  margin: 0 0 10px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const StatusList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const StatusItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #4b5563;
`;

const StatusText = styled.span`
  font-weight: 700;
  color: ${(props) =>
    props.$level === 'DANGER'
      ? '#dc2626'
      : props.$level === 'WARNING'
        ? '#d97706'
        : '#16a34a'};
`;

const TransparencyTabHeader = styled.div`
  display: flex;
  gap: 4px;
  background-color: #f3f4f6;
  padding: 3px;
  border-radius: 6px;
  margin-bottom: 12px;

  div {
    flex: 1;
    text-align: center;
    font-size: 11px;
    padding: 6px 0;
    border-radius: 4px;
    color: #6b7280;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;

    &.active {
      background-color: #ffffff;
      color: #111827;
      font-weight: 700;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }

    &:hover:not(.active) {
      color: #374151;
    }
  }
`;

const TierItem = styled.div`
  border: 1px solid ${(props) => (props.$active ? '#10b981' : '#e5e7eb')};
  background-color: ${(props) => (props.$active ? '#f0fdf4' : '#fafafa')};
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
  opacity: ${(props) => (props.$active ? 1 : 0.65)};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    opacity: 1;
  }

  .tier-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    font-weight: 700;
    color: ${(props) => (props.$active ? '#166534' : '#6b7280')};
    margin-bottom: 2px;
  }

  .tier-desc {
    font-size: 10.5px;
    color: ${(props) => (props.$active ? '#374151' : '#6b7280')};
    line-height: 1.4;
  }
`;

const FooterNote = styled.div`
  font-size: 10.5px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
`;

const ActionButton = styled.button`
  width: 100%;
  padding: 10px;
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

const getLevelText = (level) => {
  if (level === 'DANGER') return '경보';
  if (level === 'WARNING') return '주의';
  return '정상';
};

export default function RiskSidebar({ data }) {
  const navigate = useNavigate();

  // 초기 탭 상태 설정 (API 데이터 기준 우선순위: 정밀측정 > 입력값 > 동네평균만)
  const getInitialTab = () => {
    if (data?.hasPreciseData) return 'PRECISE';
    return data?.sourceType || 'OWNER_INPUT';
  };

  const [selectedTab, setSelectedTab] = useState(getInitialTab());

  // props 데이터가 새로 넘어올 경우 탭 재설정
  useEffect(() => {
    setSelectedTab(getInitialTab());
  }, [data]);

  return (
    <SidebarContainer>
      <Card>
        <Title>종합 위험 신호</Title>
        <div
          style={{
            color: '#dc2626',
            fontWeight: 700,
            fontSize: '12px',
            marginBottom: '10px',
          }}
        >
          ● {getLevelText(data?.overallRiskLevel || 'WARNING')}
        </div>
        <StatusList>
          <StatusItem>
            <span>동네 에너지 경기 신호</span>
            <StatusText $level={data?.energySignalLevel || 'WARNING'}>
              {getLevelText(data?.energySignalLevel || 'WARNING')}
            </StatusText>
          </StatusItem>
          <StatusItem>
            <span>계절·시기 선제 알림</span>
            <StatusText $level={data?.seasonalAlertLevel || 'DANGER'}>
              {getLevelText(data?.seasonalAlertLevel || 'DANGER')}
            </StatusText>
          </StatusItem>
          <StatusItem>
            <span>업종 활동 이상 변화 감지</span>
            <StatusText $level={data?.activityAnomalyLevel || 'NORMAL'}>
              {getLevelText(data?.activityAnomalyLevel || 'NORMAL')}
            </StatusText>
          </StatusItem>
          <StatusItem>
            <span>안전 리스크 체크</span>
            <StatusText $level={data?.safetyCheckLevel || 'WARNING'}>
              {getLevelText(data?.safetyCheckLevel || 'WARNING')}
            </StatusText>
          </StatusItem>
        </StatusList>
      </Card>

      <Card>
        <Title>데이터 출처 투명성</Title>
        <p style={{ fontSize: '11px', color: '#6b7280', margin: '0 0 10px 0' }}>
          "우리 가게 사용량" 데이터가 어디서 왔는지 단계로 표시해요.
        </p>

        {/* 상단 탭 클릭 시 selectedTab 변경 */}
        <TransparencyTabHeader>
          <div
            className={selectedTab === 'PRECISE' ? 'active' : ''}
            onClick={() => setSelectedTab('PRECISE')}
          >
            정밀측정
          </div>
          <div
            className={selectedTab === 'OWNER_INPUT' ? 'active' : ''}
            onClick={() => setSelectedTab('OWNER_INPUT')}
          >
            입력값
          </div>
          <div
            className={selectedTab === 'LOCAL_AVERAGE_ONLY' ? 'active' : ''}
            onClick={() => setSelectedTab('LOCAL_AVERAGE_ONLY')}
          >
            동네평균만
          </div>
        </TransparencyTabHeader>

        {/* 하단 카드 클릭 시에도 selectedTab 변경 */}
        <TierItem
          $active={selectedTab === 'PRECISE'}
          onClick={() => setSelectedTab('PRECISE')}
        >
          <div className="tier-header">
            <span>1순위 정밀측정</span>
            {selectedTab === 'PRECISE' && (
              <span
                style={{
                  color: '#10b981',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '2px',
                }}
              >
                <Check size={12} /> 현재
              </span>
            )}
          </div>
          <div className="tier-desc">
            우리 가게: 국토교통부 건물에너지정보(지번 단위)
            <br />
            동네 평균: 한국전력공사 법정동별 전력사용량
          </div>
        </TierItem>

        <TierItem
          $active={selectedTab === 'OWNER_INPUT'}
          onClick={() => setSelectedTab('OWNER_INPUT')}
        >
          <div className="tier-header">
            <span>2순위 입력값</span>
            {selectedTab === 'OWNER_INPUT' && (
              <span
                style={{
                  color: '#10b981',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '2px',
                }}
              >
                <Check size={12} /> 현재
              </span>
            )}
          </div>
          <div className="tier-desc">
            우리 가게: 사장님 입력값(전기요금 고지서 기준)
            <br />
            동네 평균: 한국전력공사 법정동별 전력사용량
          </div>
        </TierItem>

        <TierItem
          $active={selectedTab === 'LOCAL_AVERAGE_ONLY'}
          onClick={() => setSelectedTab('LOCAL_AVERAGE_ONLY')}
        >
          <div className="tier-header">
            <span>3순위 동네평균만</span>
            {selectedTab === 'LOCAL_AVERAGE_ONLY' && (
              <span
                style={{
                  color: '#10b981',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '2px',
                }}
              >
                <Check size={12} /> 현재
              </span>
            )}
          </div>
          <div className="tier-desc">
            개별 가게 비교 데이터 없음
            <br />
            동네 평균: 한국전력공사 법정동별 전력사용량 추세만 제공
          </div>
        </TierItem>

        <FooterNote>
          <Lock size={12} /> 비교 기준선 고정: 한국전력공사 법정동별 전력사용량
        </FooterNote>
      </Card>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <ActionButton
          $primary
          onClick={() => navigate('/ai-manager/saving-plan')}
        >
          <ShieldCheck size={16} /> 절감 계획 만들기
        </ActionButton>
        <ActionButton onClick={() => navigate('/ai-manager/power-report')}>
          <FileText size={16} /> 상세 리포트 보기
        </ActionButton>
      </div>
    </SidebarContainer>
  );
}
