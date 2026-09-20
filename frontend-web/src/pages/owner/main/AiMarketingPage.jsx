import { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Megaphone,
  Sparkles,
  RefreshCw,
  Send,
<<<<<<< HEAD
=======
  MessageSquare,
>>>>>>> 8d418cceda473088579385a822c4be531cdfca35
  Store,
  Loader2,
  Crown,
} from 'lucide-react';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { useAuth } from '../../../contexts/AuthContext';
import {
  AI_PAGE_REQUIRED_PLAN,
  hasRequiredPlan,
} from '../../../constants/aiPlanFeatures';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

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

const NoticeBanner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background-color: ${(props) => (props.$warning ? '#fffbeb' : '#fef2f2')};
  border: 1px solid ${(props) => (props.$warning ? '#fde68a' : '#fecaca')};
  color: ${(props) => (props.$warning ? '#92400e' : '#991b1b')};
  font-size: 13px;
  line-height: 1.5;

  button {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    border: none;
    border-radius: 8px;
    background-color: #f59e0b;
    color: #ffffff;
    font-size: 12px;
    font-weight: 700;
    cursor: pointer;
  }
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

const FooterCaption = styled.p`
  font-size: 11px;
  color: #9ca3af;
  margin-top: 8px;
`;

// UI용 탭 키 ↔ API 공지 유형 코드 매핑 (백엔드 AiNoticeType: EVENT/TEMP_CLOSED/NEW_MENU)
// 발송 채널은 상점 공지(STORE_NOTICE)만 지원한다
const CHANNELS = ['STORE_NOTICE'];

const TYPE_MAP = {
  event: 'EVENT',
  holiday: 'TEMP_CLOSED',
  menu: 'NEW_MENU',
};

// UI용 톤 키 ↔ API 톤 코드 매핑
const TONE_MAP = {
  polite: 'POLITE',
  lively: 'FRIENDLY',
  concise: 'SHORT',
};

export default function AiMarketingPage() {
  const navigate = useNavigate();
  const { aiPlanType } = useAuth();

  // 상태 관리
  const [typeTab, setTypeTab] = useState('event');
  const [tone, setTone] = useState('lively');
  const [aiText, setAiText] = useState('');
  const [loading, setLoading] = useState(false);
  const [, setOverviewData] = useState([]);
  // 플랜 부족(403/AI_001) 여부와 업그레이드 모달, 그 외 실패 안내 문구
  const [planLocked, setPlanLocked] = useState(false);
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
<<<<<<< HEAD
=======

  // 채널 활성화 상태
  const [channels, setChannels] = useState({
    KAKAO_ALERT: true,
    STORE_NOTICE: true,
    SNS_CARD: false,
  });
>>>>>>> 8d418cceda473088579385a822c4be531cdfca35

  // API 응답 데이터 저장 상태
  const [draftResult, setDraftResult] = useState({
    messageId: null,
    estimatedReach: 0,
    selectedChannelCount: 0,
    characterCount: 0,
    sendable: true,
  });

  // 마케팅 문구 초안 생성 API 호출 함수
  // auto: 페이지 진입 시 자동 생성 — 이때는 확인창(AI_015) 대신 안내 문구만 보여준다
  const fetchMarketingDraft = useCallback(
    async ({ auto = false } = {}) => {
<<<<<<< HEAD
=======
      const activeChannels = Object.keys(channels).filter(
        (key) => channels[key],
      );

      if (activeChannels.length === 0) {
        setAiText('발송할 채널을 1개 이상 선택해 주세요.');
        setDraftResult((prev) => ({
          ...prev,
          messageId: null,
          estimatedReach: 0,
          selectedChannelCount: 0,
        }));
        return;
      }

>>>>>>> 8d418cceda473088579385a822c4be531cdfca35
      setLoading(true);
      setErrorMessage('');

      const createDraft = (confirmDelete) =>
        aiManagerApi.createMarketingDraft({
          noticeType: TYPE_MAP[typeTab],
          tone: TONE_MAP[tone],
<<<<<<< HEAD
          channels: CHANNELS,
=======
          channels: activeChannels,
>>>>>>> 8d418cceda473088579385a822c4be531cdfca35
          confirmDelete,
        });

      try {
        let response;
        try {
          response = await createDraft(false);
        } catch (error) {
          const body = error.response?.data;
          // AI_015: 초안 보관 개수 초과 — 사장님 확인 후 confirmDelete=true 로 재요청
          if (body?.error?.code === 'AI_015' && !auto) {
            const capacity = body.data;
            const detail = capacity
              ? `\n(보관 중 ${capacity.currentCount}개 / 최대 ${capacity.limit}개)`
              : '';
            if (!window.confirm(`${body.error.message}${detail}`)) return;
            response = await createDraft(true);
          } else {
            throw error;
          }
        }

        if (response.data?.success && response.data?.data) {
          const resData = response.data.data;
          setPlanLocked(false);
          setAiText(resData.content || '');
          setDraftResult({
            messageId: resData.messageId,
            estimatedReach: resData.estimatedReach || 0,
            selectedChannelCount:
              resData.selectedChannelCount || CHANNELS.length,
            characterCount:
              resData.characterCount ||
              (resData.content ? resData.content.length : 0),
            sendable: resData.sendable ?? true,
          });
        }
      } catch (error) {
        const body = error.response?.data;
        const code = body?.error?.code;

        if (error.response?.status === 403 || code === 'AI_001') {
          // FREE 플랜 — 조용히 실패하지 않고 업그레이드를 안내한다
          setPlanLocked(true);
          if (!auto) setIsUpgradeModalOpen(true);
        } else if (code === 'AI_015') {
          setErrorMessage(
            '초안 보관함이 가득 찼어요. "다시 생성"을 누르면 오래된 초안을 정리하고 새로 만들 수 있어요.',
          );
        } else {
          console.error('마케팅 문구 초안 생성 실패:', error);
          setErrorMessage(
            body?.error?.message ||
              '문구 생성에 실패했어요. 잠시 후 다시 시도해 주세요.',
          );
        }
        setDraftResult((prev) => ({ ...prev, messageId: null }));
      } finally {
        setLoading(false);
      }
    },
    [typeTab, tone],
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
      // 캐싱된 플랜으로 BASIC 미만이 확실하면 초안 생성 요청(사용량 차감 대상)을 생략한다
      if (!hasRequiredPlan(aiPlanType, AI_PAGE_REQUIRED_PLAN.marketingDraft)) {
        setPlanLocked(true);
        return;
      }
      // 최초 초안 가져오기
      fetchMarketingDraft({ auto: true });
    };

    fetchInitialData();
  }, []); // 의존성 배열을 비워 최초 1회만 실행

  // 새로고침 직후에는 플랜 조회가 끝나기 전에 초안 요청이 먼저 나갈 수 있다 —
  // 플랜이 뒤늦게 BASIC 미만으로 확정되면 그때 화면을 잠근다
  useEffect(() => {
    if (!hasRequiredPlan(aiPlanType, AI_PAGE_REQUIRED_PLAN.marketingDraft)) {
      queueMicrotask(() => setPlanLocked(true));
    }
  }, [aiPlanType]);
<<<<<<< HEAD
=======

  const handleToggleChannel = (key) => {
    setChannels((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const activeChannelCount = Object.values(channels).filter(Boolean).length;
>>>>>>> 8d418cceda473088579385a822c4be531cdfca35

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
            공지 유형과 톤만 고르면 AI가 홍보 문구를 만들어 매장 공지로
            올려드려요.
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
                onClick={() =>
                  planLocked
                    ? setIsUpgradeModalOpen(true)
                    : fetchMarketingDraft()
                }
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
              {draftResult.characterCount || aiText.length}자 · 매장 공지로 게시
              가능
            </CharacterCount>

            {planLocked && (
              <NoticeBanner $warning>
                <span>
                  마케팅 문구 생성은 베이직 플랜부터 이용할 수 있어요.
                </span>
                <button
                  type="button"
                  onClick={() => setIsUpgradeModalOpen(true)}
                >
                  <Crown size={12} /> 플랜 업그레이드
                </button>
              </NoticeBanner>
            )}
            {errorMessage && !planLocked && (
              <NoticeBanner>{errorMessage}</NoticeBanner>
            )}
          </Card>

          <Card>
            <CardHeader>
              <CardTitleGroup>
                <h4>발송 채널</h4>
                <p>공지는 우리 가게 매장 페이지에 게시돼요</p>
              </CardTitleGroup>
            </CardHeader>

            <ChannelList>
              <ChannelItem $active>
                <ChannelInfo $active>
                  <Store size={18} /> 매장 공지
                </ChannelInfo>
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
                <span className="value">매장 공지</span>
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
            onClick={() =>
              navigate('/ai-manager/notice', {
                state: {
                  noticeData: {
                    messageId: draftResult.messageId,
                    // 사장님이 고친 문구가 그대로 넘어가야 한다
                    content: aiText,
                    estimatedReach: draftResult.estimatedReach,
                    noticeType: TYPE_MAP[typeTab],
                  },
                },
              })
            }
            disabled={
              planLocked ||
              !draftResult.sendable ||
              !draftResult.messageId ||
              loading
            }
          >
            <Send size={16} /> 공지 등록하기
          </PrimaryButton>
        </RightColumn>
      </ContentGrid>

      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => setIsUpgradeModalOpen(false)}
        errorMessage="마케팅 문구 생성은 베이직 플랜부터 이용할 수 있어요."
      />
    </PageContainer>
  );
}
