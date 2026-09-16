import styled from 'styled-components';
import {
  Megaphone,
  ChevronRight,
  Sparkles,
  Send,
  RefreshCw,
  Loader2,
  Crown,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { useAuth } from '../../../contexts/AuthContext';
import {
  AI_PAGE_REQUIRED_PLAN,
  hasRequiredPlan,
} from '../../../constants/aiPlanFeatures';
import PlanUpgradeModal from './modal/PlanUpgradeModal';

// UI 탭 키 ↔ 백엔드 AiNoticeType 매핑 (AiMarketingPage.jsx와 동일 규칙)
const TYPE_MAP = {
  event: 'EVENT',
  holiday: 'TEMP_CLOSED',
  menu: 'NEW_MENU',
};

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
  const { aiPlanType } = useAuth();
  const [activeTab, setActiveTab] = useState('event');
  const [draft, setDraft] = useState(null);
  const [loading, setLoading] = useState(true);
  const [planLocked, setPlanLocked] = useState(false);
  const [draftLimitReached, setDraftLimitReached] = useState(false);
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [lockBusy, setLockBusy] = useState(false);
  const navigate = useNavigate();

  // fetchDraft가 겹쳐서(동시에 두 번) 호출되는 걸 막는 가드. React StrictMode는
  // 개발 모드에서 effect를 일부러 두 번 실행하는데, 그 두 호출이 같은 상점의
  // 사용량 카운트 분산 락(LockKeys.aiUsage)에 거의 동시에 붙으면 뒤 요청이
  // LOCK_001(락 획득 실패)로 튕긴다 — 애초에 두 번째 요청 자체를 안 보내야 한다.
  const isFetchingRef = useRef(false);

  const fetchDraft = useCallback(async () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;
    try {
      setLoading(true);
      const response = await aiManagerApi.createMarketingDraft({
        noticeType: TYPE_MAP[activeTab],
        tone: 'FRIENDLY',
        channels: ['KAKAO_ALERT', 'STORE_NOTICE'],
        confirmDelete: false,
      });
      if (response.data?.success) {
        setDraft(response.data.data);
        setPlanLocked(false);
        setDraftLimitReached(false);
        setLockBusy(false);
      }
    } catch (error) {
        console.error('마케팅 문구 초안 생성 실패:', error);
      setDraft(null);
    } finally {
      isFetchingRef.current = false;
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => {
    // 캐싱된 플랜으로 이미 BASIC 미만인 게 확실하면 초안 생성 요청(사용량
    // 차감 대상) 자체를 생략한다 — 매번 403 + 콘솔 에러 로그가 남는 것도
    // 막고, 도달 불가능한 기능을 위해 사용량을 낭비하지도 않는다.
    if (!hasRequiredPlan(aiPlanType, AI_PAGE_REQUIRED_PLAN.marketingDraft)) {
      queueMicrotask(() => {
        setPlanLocked(true);
        setLoading(false);
      });
      return;
    }
    queueMicrotask(() => fetchDraft());

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
          {loading
            ? '문구를 생성하는 중...'
                : draft?.content || '문구 생성에 실패했습니다. 다시 시도해주세요.'}
        </AiMessageText>
      </AiMessageBox>

      {/* 하단 버튼 그룹 */}
      <ActionRow>
        <SubmitButton
          disabled={!draft}
          onClick={() =>
            navigate('/ai-manager/notice', {
              state: {
                noticeData: {
                  messageId: draft?.messageId,
                  content: draft?.content,
                  estimatedReach: draft?.estimatedReach,
                },
              },
            })
          }
        >
          <Send size={16} /> 공지 등록하기
        </SubmitButton>
        <RefreshButton
          onClick={fetchDraft}
          disabled={loading}
        >
          {loading ? <Loader2 size={14} /> : <RefreshCw size={14} />} 다시 생성
        </RefreshButton>
      </ActionRow>
    </CardContainer>
  );
}
