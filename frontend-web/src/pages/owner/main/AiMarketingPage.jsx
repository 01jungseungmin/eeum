import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Megaphone,
  Sparkles,
  RefreshCw,
  Send,
  Clock,
  MessageSquare,
  Store,
  Share2,
  Loader2,
} from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

const PageContainer = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  background-color: #f8fafc;
  min-height: 100vh;
  box-sizing: border-box;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  margin-bottom: 20px;
  transition: color 0.2s;

  &:hover {
    color: #111827;
  }
`;

const HeaderSection = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
`;

const IconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background-color: #e0f2fe;
  color: #0284c7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const HeaderTitleGroup = styled.div`
  display: flex;
  flex-direction: column;
`;

const TitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  h2 {
    font-size: 22px;
    font-weight: 800;
    color: #111827;
    margin: 0;
  }
`;

const Badge = styled.span`
  background-color: #e6f4ed;
  color: #2e7d5d;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
`;

const HeaderSubtitle = styled.p`
  font-size: 13px;
  color: #6b7280;
  margin: 4px 0 0 0;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const LeftColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const RightColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const Card = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
`;

const CardTitleGroup = styled.div`
  h4 {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  p {
    font-size: 12px;
    color: #9ca3af;
    margin: 4px 0 0 0;
  }
`;

const TabContainer = styled.div`
  display: flex;
  background-color: #f3f4f6;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 20px;
`;

const Tab = styled.button`
  flex: 1;
  background-color: ${(props) => (props.$active ? '#ffffff' : 'transparent')};
  color: ${(props) => (props.$active ? '#111827' : '#6b7280')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  border: none;
  padding: 10px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  box-shadow: ${(props) =>
    props.$active ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'};
  transition: all 0.2s;
`;

const ToneLabel = styled.div`
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
`;

const ToneGroup = styled.div`
  display: flex;
  gap: 8px;
`;

const ToneChip = styled.button`
  background-color: ${(props) => (props.$active ? '#e6f4ed' : '#ffffff')};
  color: ${(props) => (props.$active ? '#2e7d5d' : '#6b7280')};
  border: 1px solid ${(props) => (props.$active ? '#47a075' : '#e5e7eb')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #47a075;
  }
`;

const RefreshTextButton = styled.button`
  background: none;
  border: none;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;

  &:hover {
    color: #111827;
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const AiMessageContainer = styled.div`
  background-color: #f0fdf4;
  border: 1px dashed #bbf7d0;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  position: relative;
`;

const AiMessageBadge = styled.div`
  font-size: 12px;
  font-weight: 700;
  color: #15803d;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
`;

const AiTextArea = styled.textarea`
  width: 100%;
  border: none;
  background: transparent;
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  resize: none;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
`;

const CharacterCount = styled.div`
  font-size: 12px;
  color: #9ca3af;
`;

const ChannelList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ChannelItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 12px;
  background-color: ${(props) => (props.$active ? '#e6f4ed' : '#f9fafb')};
  border: 1px solid ${(props) => (props.$active ? '#a7f3d0' : '#f3f4f6')};
  transition: all 0.2s;
`;

const ChannelInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  color: ${(props) => (props.$active ? '#065f46' : '#374151')};
  font-weight: 600;
  font-size: 14px;
`;

const ToggleSwitch = styled.label`
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;

  input {
    opacity: 0;
    width: 0;
    height: 0;
  }

  span {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #d1d5db;
    transition: 0.3s;
    border-radius: 24px;
  }

  span:before {
    position: absolute;
    content: '';
    height: 18px;
    width: 18px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    transition: 0.3s;
    border-radius: 50%;
  }

  input:checked + span {
    background-color: #47a075;
  }

  input:checked + span:before {
    transform: translateX(20px);
  }
`;

const SummaryTable = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SummaryRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;

  span.label {
    color: #6b7280;
  }
  span.value {
    font-weight: 700;
    color: #111827;
  }
  span.highlight {
    font-weight: 800;
    color: #47a075;
  }
`;

const Divider = styled.hr`
  border: none;
  border-top: 1px solid #f3f4f6;
  margin: 4px 0;
`;

const PrimaryButton = styled.button`
  width: 100%;
  background-color: #47a075;
  color: #ffffff;
  border: none;
  padding: 14px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #3b8762;
  }
  &:disabled {
    background-color: #9ca3af;
    cursor: not-allowed;
  }
`;

const SecondaryButton = styled.button`
  width: 100%;
  background-color: #ffffff;
  color: #374151;
  border: 1px solid #e5e7eb;
  padding: 14px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }
`;

const FooterCaption = styled.p`
  font-size: 11px;
  color: #9ca3af;
  margin-top: 8px;
`;

// UI용 탭 키 ↔ API 공지 유형 코드 매핑
const TYPE_MAP = {
  event: 'EVENT',
  holiday: 'HOLIDAY',
  menu: 'MENU',
};

// UI용 톤 키 ↔ API 톤 코드 매핑
const TONE_MAP = {
  polite: 'POLITE',
  lively: 'FRIENDLY',
  concise: 'SHORT',
};

export default function AiMarketingPage() {
  const navigate = useNavigate();

  // 상태 관리
  const [typeTab, setTypeTab] = useState('event');
  const [tone, setTone] = useState('lively');
  const [aiText, setAiText] = useState('');
  const [loading, setLoading] = useState(false);

  // 개요 정보 상태 (GET /owner/ai-manager/marketing)
  const [overviewData, setOverviewData] = useState([]);

  // 채널 활성화 상태
  const [channels, setChannels] = useState({
    KAKAO_ALERT: true,
    STORE_NOTICE: true,
    SNS_CARD: false,
  });

  // API 응답 데이터 저장 상태
  const [draftResult, setDraftResult] = useState({
    messageId: null,
    estimatedReach: 0,
    selectedChannelCount: 0,
    characterCount: 0,
    sendable: true,
  });

  // 마케팅 문구 초안 생성 API 호출 함수
  const fetchMarketingDraft = useCallback(
    async (confirmDelete = false) => {
      const activeChannels = Object.keys(channels).filter(
        (key) => channels[key],
      );

      if (activeChannels.length === 0) {
        setAiText('발송할 채널을 1개 이상 선택해 주세요.');
        setDraftResult((prev) => ({
          ...prev,
          estimatedReach: 0,
          selectedChannelCount: 0,
        }));
        return;
      }

      setLoading(true);

      const requestBody = {
        noticeType: TYPE_MAP[typeTab],
        tone: TONE_MAP[tone],
        channels: activeChannels,
        confirmDelete,
      };

      try {
        const response = await aiManagerApi.createMarketingDraft(requestBody);
        if (response.data?.success && response.data?.data) {
          const resData = response.data.data;
          setAiText(resData.content || '');
          setDraftResult({
            messageId: resData.messageId,
            estimatedReach: resData.estimatedReach || 0,
            selectedChannelCount:
              resData.selectedChannelCount || activeChannels.length,
            characterCount:
              resData.characterCount ||
              (resData.content ? resData.content.length : 0),
            sendable: resData.sendable ?? true,
          });
        }
      } catch (error) {
        console.error('마케팅 문구 초안 생성 실패:', error);
      } finally {
        setLoading(false);
      }
    },
    [typeTab, tone, channels],
  );

  // 페이지 진입 시 초기 데이터 1회만 조회
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const overviewRes = await aiManagerApi.getMarketingOverview();
        if (overviewRes.data?.success && overviewRes.data?.data) {
          setOverviewData(overviewRes.data.data);
        }
      } catch (error) {
        console.error('마케팅 개요 조회 실패:', error);
      }
      // 최초 초안 가져오기
      fetchMarketingDraft();
    };

    fetchInitialData();
  }, []); // 의존성 배열을 비워 최초 1회만 실행

  const handleToggleChannel = (key) => {
    setChannels((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const activeChannelCount = Object.values(channels).filter(Boolean).length;

  const toneLabelMap = {
    polite: '정정한 톤',
    lively: '발랄한 톤',
    short: '간결한 톤',
  };

  const typeLabelMap = {
    event: '이벤트',
    holiday: '임시휴무',
    menu: '신메뉴',
  };

  return (
    <PageContainer>
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={18} /> AI 매니저로 돌아가기
      </BackButton>

      <HeaderSection>
        <IconBox>
          <Megaphone size={24} />
        </IconBox>
        <HeaderTitleGroup>
          <TitleRow>
            <h2>마케팅 매니저</h2>
            <Badge>
              <Sparkles size={13} /> AI 분석 완료
            </Badge>
          </TitleRow>
          <HeaderSubtitle>
            공지 유형과 톤만 고르면 AI가 홍보 문구를 만들어 여러 채널로
            보내드려요.
          </HeaderSubtitle>
        </HeaderTitleGroup>
      </HeaderSection>

      <ContentGrid>
        <LeftColumn>
          <Card>
            <CardHeader>
              <CardTitleGroup>
                <h4>공지 유형</h4>
                <p>만들 공지의 종류를 선택하세요</p>
              </CardTitleGroup>
            </CardHeader>

            <TabContainer>
              <Tab
                $active={typeTab === 'event'}
                onClick={() => setTypeTab('event')}
              >
                이벤트
              </Tab>
              <Tab
                $active={typeTab === 'holiday'}
                onClick={() => setTypeTab('holiday')}
              >
                임시휴무
              </Tab>
              <Tab
                $active={typeTab === 'menu'}
                onClick={() => setTypeTab('menu')}
              >
                신메뉴
              </Tab>
            </TabContainer>

            <ToneLabel>문구 톤</ToneLabel>
            <ToneGroup>
              <ToneChip
                $active={tone === 'polite'}
                onClick={() => setTone('polite')}
              >
                정정한
              </ToneChip>
              <ToneChip
                $active={tone === 'lively'}
                onClick={() => setTone('lively')}
              >
                발랄한
              </ToneChip>
              <ToneChip
                $active={tone === 'concise'}
                onClick={() => setTone('concise')}
              >
                간결한
              </ToneChip>
            </ToneGroup>
          </Card>

          <Card>
            <CardHeader>
              <CardTitleGroup>
                <h4>AI 생성 문구</h4>
                <p>자유롭게 수정한 뒤 등록할 수 있어요</p>
              </CardTitleGroup>
              <RefreshTextButton
                onClick={() => fetchMarketingDraft(false)}
                disabled={loading}
              >
                {loading ? (
                  <Loader2
                    size={14}
                    className="animate-spin"
                  />
                ) : (
                  <RefreshCw size={14} />
                )}
                다시 생성
              </RefreshTextButton>
            </CardHeader>

            <AiMessageContainer>
              <AiMessageBadge>
                <Sparkles size={14} /> AI 생성 문구 · {toneLabelMap[tone]} · 1안
              </AiMessageBadge>
              <AiTextArea
                rows={4}
                value={aiText}
                onChange={(e) => setAiText(e.target.value)}
                placeholder={loading ? 'AI가 문구를 생성 중입니다...' : ''}
                disabled={loading}
              />
            </AiMessageContainer>

            <CharacterCount>
              {draftResult.characterCount || aiText.length}자 · 알림톡 1건으로
              발송 가능
            </CharacterCount>
          </Card>

          <Card>
            <CardHeader>
              <CardTitleGroup>
                <h4>발송 채널</h4>
                <p>공지를 내보낼 채널을 선택하세요</p>
              </CardTitleGroup>
            </CardHeader>

            <ChannelList>
              <ChannelItem $active={channels.KAKAO_ALERT}>
                <ChannelInfo $active={channels.KAKAO_ALERT}>
                  <MessageSquare size={18} /> 알림톡
                </ChannelInfo>
                <ToggleSwitch>
                  <input
                    type="checkbox"
                    checked={channels.KAKAO_ALERT}
                    onChange={() => handleToggleChannel('KAKAO_ALERT')}
                  />
                  <span />
                </ToggleSwitch>
              </ChannelItem>

              <ChannelItem $active={channels.STORE_NOTICE}>
                <ChannelInfo $active={channels.STORE_NOTICE}>
                  <Store size={18} /> 매장 공지
                </ChannelInfo>
                <ToggleSwitch>
                  <input
                    type="checkbox"
                    checked={channels.STORE_NOTICE}
                    onChange={() => handleToggleChannel('STORE_NOTICE')}
                  />
                  <span />
                </ToggleSwitch>
              </ChannelItem>

              <ChannelItem $active={channels.SNS_CARD}>
                <ChannelInfo $active={channels.SNS_CARD}>
                  <Share2 size={18} /> SNS 카드
                </ChannelInfo>
                <ToggleSwitch>
                  <input
                    type="checkbox"
                    checked={channels.SNS_CARD}
                    onChange={() => handleToggleChannel('SNS_CARD')}
                  />
                  <span />
                </ToggleSwitch>
              </ChannelItem>
            </ChannelList>
          </Card>

          <FooterCaption>출처: 이음 고객 활동·발송 반응 데이터</FooterCaption>
        </LeftColumn>

        <RightColumn>
          <Card>
            <CardHeader>
              <CardTitleGroup>
                <p style={{ fontSize: '13px', color: '#6b7280', margin: 0 }}>
                  발송 요약
                </p>
              </CardTitleGroup>
            </CardHeader>

            <SummaryTable>
              <SummaryRow>
                <span className="label">유형</span>
                <span className="value">{typeLabelMap[typeTab]}</span>
              </SummaryRow>

              <Divider />

              <SummaryRow>
                <span className="label">톤</span>
                <span className="value">{toneLabelMap[tone]}</span>
              </SummaryRow>

              <Divider />

              <SummaryRow>
                <span className="label">채널</span>
                <span className="value">{activeChannelCount}개 선택</span>
              </SummaryRow>

              <Divider />

              <SummaryRow>
                <span className="label">추정 도달</span>
                <span className="highlight">
                  약 {draftResult.estimatedReach.toLocaleString()}명
                </span>
              </SummaryRow>
            </SummaryTable>
          </Card>

          <PrimaryButton
            onClick={() => navigate('/ai-manager/notice')}
            disabled={!draftResult.sendable || loading}
          >
            <Send size={16} /> 공지 등록하기
          </PrimaryButton>
          <SecondaryButton disabled={loading}>
            <Clock size={16} /> 예약 발송
          </SecondaryButton>
        </RightColumn>
      </ContentGrid>
    </PageContainer>
  );
}
