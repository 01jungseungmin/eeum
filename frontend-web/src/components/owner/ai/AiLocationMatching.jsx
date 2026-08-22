import React from 'react';
import styled from 'styled-components';
import { MapPin, ChevronRight, Crown, Users } from 'lucide-react';

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

/* CSS conic-gradient 기반 도넛 차트 */
const DonutChart = styled.div`
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: conic-gradient(#34d399 0% 92%, #e5e7eb 92% 100%);
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
        <MoreButton>
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
          <DonutChart />
          <DonutText>
            92<span>점</span>
          </DonutText>
        </div>
        <ScoreDesc>
          <h4>매칭 점수가 아주 높아요</h4>
          <p>
            주변 생활권 고객의 관심사와 잘 맞아요. 지금 노출하면 효과가 커요.
          </p>
        </ScoreDesc>
      </ChartContent>

      {/* 태그 목록 */}
      <TagSection>
        <TagTitle>매칭 이유</TagTitle>
        <TagGroup>
          <Tag>#서울마포구</Tag>
          <Tag>#한식관심</Tag>
          <Tag>#점심이벤트진행중</Tag>
          <Tag>#단골고객多</Tag>
        </TagGroup>
      </TagSection>

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

      <SubmitButton>
        <Users size={16} /> 노출 대상 확인하기
      </SubmitButton>
    </CardContainer>
  );
}
