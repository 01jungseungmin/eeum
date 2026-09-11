import React, { useState } from 'react';
import styled from 'styled-components';
import {
  Megaphone,
  ChevronRight,
  Sparkles,
  Send,
  RefreshCw,
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
  background-color: #e0f2fe;
  color: #0284c7;
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

const TabContainer = styled.div`
  display: flex;
  background-color: #f3f4f6;
  border-radius: 10px;
  padding: 4px;
`;

const Tab = styled.button`
  flex: 1;
  background-color: ${(props) => (props.$active ? '#ffffff' : 'transparent')};
  color: ${(props) => (props.$active ? '#111827' : '#6b7280')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  border: none;
  padding: 8px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  box-shadow: ${(props) =>
    props.$active ? '0 1px 3px rgba(0,0,0,0.1)' : 'none'};
  transition: all 0.2s;
`;

const AiMessageBox = styled.div`
  background-color: #f0fdf4;
  border: 1px dashed #bbf7d0;
  border-radius: 12px;
  padding: 16px;
`;

const AiMessageLabel = styled.div`
  font-size: 12px;
  font-weight: 700;
  color: #15803d;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
`;

const AiMessageText = styled.p`
  font-size: 13px;
  color: #374151;
  margin: 0;
  line-height: 1.5;
`;

const ActionRow = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;

const SubmitButton = styled.button`
  flex: 1;
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

const RefreshButton = styled.button`
  background: none;
  border: none;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 8px;

  &:hover {
    color: #111827;
  }
`;

export default function AiMarketingAutomation() {
  const [activeTab, setActiveTab] = useState('event');
  const navigate = useNavigate();

  return (
    <CardContainer id="section-ai-marketing">
      <Header>
        <HeaderLeft>
          <IconBox>
            <Megaphone size={20} />
          </IconBox>
          <TitleArea>
            <h3>마케팅 자동화 매니저</h3>
            <p>공지·문구 생성 엔진</p>
          </TitleArea>
        </HeaderLeft>
        <MoreButton onClick={() => navigate('/ai-manager/marketing')}>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      {/* 탭 버튼 */}
      <TabContainer>
        <Tab
          $active={activeTab === 'event'}
          onClick={() => setActiveTab('event')}
        >
          이벤트
        </Tab>
        <Tab
          $active={activeTab === 'holiday'}
          onClick={() => setActiveTab('holiday')}
        >
          임시휴무
        </Tab>
        <Tab
          $active={activeTab === 'menu'}
          onClick={() => setActiveTab('menu')}
        >
          신메뉴
        </Tab>
      </TabContainer>

      {/* AI 문구 박스 */}
      <AiMessageBox>
        <AiMessageLabel>
          <Sparkles size={14} /> AI 생성 문구
        </AiMessageLabel>
        <AiMessageText>
          🎉 [맛있는 반찬가게] 평일 점심 한정 이벤트!
          <br />
          김치찌개 반찬 세트를 10% 할인된 가격에 만나보세요. 정성 가득한 집밥
          반찬, 오늘 점심은 이음에서 주문해 보세요 😋
        </AiMessageText>
      </AiMessageBox>

      {/* 하단 버튼 그룹 */}
      <ActionRow>
        <SubmitButton onClick={() => navigate('/ai-manager/notice')}>
          <Send size={16} /> 공지 등록하기
        </SubmitButton>
        <RefreshButton>
          <RefreshCw size={14} /> 다시 생성
        </RefreshButton>
      </ActionRow>
    </CardContainer>
  );
}
