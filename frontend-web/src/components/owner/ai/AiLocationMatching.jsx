import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { MapPin, ChevronRight, Crown, Users } from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi'; // 파일 경로에 맞게 수정
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
  background-color: #f0f3ff;
  color: #4f46e5;
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

const SubText = styled.p`
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0 0;
`;

const Badge = styled.span`
  background-color: #f3f4f6;
  color: #4b5563;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
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

const ChartContent = styled.div`
  display: flex;
  align-items: center;
  gap: 20px;
  margin: 8px 0;
`;

/* score -> $score 로 수정하여 전달된 prop을 정상 인식하도록 함 */
const DonutChart = styled.div`
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: ${({ $score }) =>
    `conic-gradient(#34d399 0% ${$score}%, #e5e7eb ${$score}% 100%)`};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &::after {
    content: '';
    width: 74px;
    height: 74px;
    background-color: #ffffff;
    border-radius: 50%;
  }
`;

const DonutText = styled.div`
  position: absolute;
  font-size: 22px;
  font-weight: 800;
  color: #111827;
  display: flex;
  align-items: baseline;

  span {
    font-size: 13px;
    font-weight: 600;
    margin-left: 2px;
  }
`;

const ScoreDesc = styled.div`
  h4 {
    font-size: 16px;
    font-weight: 700;
    color: #065f46;
    margin: 0 0 6px 0;
  }
  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
    line-height: 1.4;
  }
`;

const TagSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const TagTitle = styled.span`
  font-size: 12px;
  color: #9ca3af;
  font-weight: 500;
`;

const TagGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
`;

const Tag = styled.span`
  background-color: #f0f3ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
`;

const ProBanner = styled.div`
  background-color: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 12px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 6px;

  span {
    font-weight: 700;
    color: #d97706;
  }
`;

const SubmitButton = styled.button`
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

export default function AiLocationMatching() {
  const [matchData, setMatchData] = useState(null);
  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  useEffect(() => {
    const fetchLocalMatch = async () => {
      try {
        const response = await aiManagerApi.getLocalMatchAnalysis();
        if (response.data && response.data.success) {
          setMatchData(response.data.data);
        }
      } catch (error) {
        console.error('생활권 매칭 데이터 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchLocalMatch();
  }, []);

  if (loading) return <div>로딩 중...</div>;
  if (!matchData) return null;

  const {
    totalScore,
    segments,
    matchReason,
    estimatedTargetCount,
    hasData,
    emptyMessage,
  } = matchData;

  // 데이터가 없을 때의 예외 처리
  if (!hasData) {
    return (
      <CardContainer id="section-ai-location">
        <Header>
          <HeaderLeft>
            <IconBox>
              <MapPin size={20} />
            </IconBox>
            <div>
              <TitleArea>
                <h3>생활권 매칭 매니저</h3>
              </TitleArea>
            </div>
          </HeaderLeft>
        </Header>
        <p style={{ color: '#6b7280', textAlign: 'center', padding: '20px 0' }}>
          {emptyMessage || '분석할 데이터가 부족합니다.'}
        </p>
      </CardContainer>
    );
  }

  return (
    <CardContainer id="section-ai-location">
      <Header>
        <HeaderLeft>
          <IconBox>
            <MapPin size={20} />
          </IconBox>
          <div>
            <TitleArea>
              <h3>생활권 매칭 매니저</h3>
              <Badge>일부 Pro</Badge>
            </TitleArea>
            <SubText>매칭 점수</SubText>
          </div>
        </HeaderLeft>
        <MoreButton onClick={() => navigate('/ai-manager/location')}>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      {/* 점수 & 그래프 */}
      <ChartContent>
        <div
          style={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <DonutChart $score={totalScore} />
          <DonutText>
            {totalScore}
            <span>점</span>
          </DonutText>
        </div>
        <ScoreDesc>
          <h4>
            {totalScore >= 80 ? '매칭 점수가 아주 높아요' : '매칭 분석 완료'}
          </h4>
          <p>{matchReason}</p>
        </ScoreDesc>
      </ChartContent>

      {/* 태그 목록 (segments 배열 동적 바인딩) */}
      {segments && segments.length > 0 && (
        <TagSection>
          <TagTitle>매칭 이유</TagTitle>
          <TagGroup>
            {segments.map((tag, index) => (
              <Tag key={index}>#{tag}</Tag>
            ))}
          </TagGroup>
        </TagSection>
      )}

      {/* Pro 배너 */}
      <ProBanner>
        <Crown
          size={14}
          color="#d97706"
        />
        <div>
          <span>AI Pro:</span> 매칭 점수가 높은 고객군에게 우선 노출할 수 있어요
        </div>
      </ProBanner>

      <SubmitButton onClick={() => navigate('/ai-manager/location')}>
        <Users size={16} /> 노출 대상 확인하기 ({estimatedTargetCount}명)
      </SubmitButton>
    </CardContainer>
  );
}
