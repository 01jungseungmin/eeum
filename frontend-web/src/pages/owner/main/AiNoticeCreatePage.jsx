import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import styled from 'styled-components';
import {
  ArrowLeft,
  Sparkles,
  RotateCw,
  MessageSquare,
  Bell,
  Store,
  Send,
  Calendar as CalendarIcon,
  Clock,
} from 'lucide-react';

import { aiManagerApi } from '../../../api/owner/aiManagerApi';

// 백엔드 AiNoticeType(EVENT/TEMP_CLOSED/NEW_MENU)과 동일
const NOTICE_TYPES = [
  { value: 'EVENT', label: '이벤트 안내' },
  { value: 'TEMP_CLOSED', label: '임시 휴무 안내' },
  { value: 'NEW_MENU', label: '신메뉴 소식' },
];

const KEYWORD_MAX_LENGTH = 100;
// 백엔드 AiGeneratedMessageUpdateRequestDto.content 상한
const NOTICE_TEXT_MAX_LENGTH = 2000;

// date input은 로컬 날짜 기준이어야 하므로 toISOString(UTC) 대신 직접 포맷한다
const toDateInputValue = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

const getTodayString = () => toDateInputValue(new Date());

// 예약 발송 기본 날짜는 내일 — 오늘 10:00이 이미 지났을 수 있어서 과거 시각 기본값을 피한다
const getTomorrowString = () => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  return toDateInputValue(tomorrow);
};

export default function AiNoticeCreatePage() {
  const navigate = useNavigate();
  const location = useLocation();

  // 이전 페이지(대시보드/카드)에서 넘어온 데이터가 있다면 초기값으로 사용
  const initialNoticeData = location.state?.noticeData;

  // API 및 폼 상태관리
  const [messageId, setMessageId] = useState(
    initialNoticeData?.messageId || null,
  );
  const [noticeText, setNoticeText] = useState(
    initialNoticeData?.content ||
      '생성할 공지 문구 조건(채널 등)을 확인 후 [문구 다시 생성] 버튼을 눌러주세요.',
  );
  const [estimatedReach, setEstimatedReach] = useState(
    initialNoticeData?.estimatedReach || 0,
  );
  const [isLoading, setIsLoading] = useState(false);

  // 공지 유형 / 키워드 — 이전 화면(마케팅)에서 넘어온 유형이 있으면 그대로 이어받는다
  const [noticeType, setNoticeType] = useState(
    NOTICE_TYPES.some((type) => type.value === initialNoticeData?.noticeType)
      ? initialNoticeData.noticeType
      : 'EVENT',
  );
  const [keyword, setKeyword] = useState('');

  // 채널 선택 상태
  const [channels, setChannels] = useState({
    KAKAO_ALERT: true,
    APP_PUSH: false,
    STORE_NOTICE: false,
  });

  // 발송 시간 탭 상태: 'IMMEDIATE' | 'RESERVED'
  const [sendType, setSendType] = useState('IMMEDIATE');
  const [scheduledDate, setScheduledDate] = useState(getTomorrowString);
  const [scheduledTime, setScheduledTime] = useState('10:00');

  // [수정] 수동 호출 전용: 사용자가 '문구 다시 생성' 버튼을 누를 때만 실행
  const handleGenerateNoticeDraft = async (confirmDelete = false) => {
    setIsLoading(true);
    const selectedChannels = Object.keys(channels).filter(
      (key) => channels[key],
    );

    if (selectedChannels.length === 0) {
      alert('최소 하나의 채널을 선택해야 합니다.');
      setIsLoading(false);
      return;
    }

    const trimmedKeyword = keyword.trim();
    const requestBody = {
      noticeType,
      tone: 'FRIENDLY',
      channels: selectedChannels,
      // 키워드는 선택 입력 — 비어 있으면 필드 자체를 보내지 않는다
      ...(trimmedKeyword && { keyword: trimmedKeyword }),
      confirmDelete: confirmDelete,
    };

    try {
      const response = await aiManagerApi.createNoticeDraft(requestBody);
      const result = response.data;

      if (result?.success) {
        setMessageId(result.data.messageId);
        setNoticeText(result.data.content);
        setEstimatedReach(result.data.estimatedReach);
      }
    } catch (error) {
      console.error('Draft generation error:', error);

      const errorBody = error.response?.data;

      // AI_015: 초안 보관 개수 초과 — 확인 후 confirmDelete=true 로 재요청
      if (errorBody?.error?.code === 'AI_015') {
        const isConfirm = window.confirm(
          '초안 보관 개수를 초과했습니다. 가장 오래된 초안을 삭제하고 새 초안을 생성하시겠습니까?',
        );
        if (isConfirm) {
          return handleGenerateNoticeDraft(true);
        }
      } else {
        alert(
          errorBody?.error?.message || '초안 생성 중 오류가 발생했습니다.',
        );
      }
    } finally {
      setIsLoading(false);
    }
  };

  // [수정] 자동 생성 useEffect 제거 완료 (더 이상 진입 시/채널 변경 시 자동 생성 API를 부르지 않음)

  const handlePublishNotice = async () => {
    if (!messageId) {
      alert('등록할 공지 초안이 없습니다. 문구를 먼저 생성해 주세요.');
      return;
    }
    if (!noticeText.trim()) {
      alert('공지 문구를 입력해 주세요.');
      return;
    }

    const isReserved = sendType === 'RESERVED';
    const formattedScheduledAt = `${scheduledDate}T${scheduledTime}:00`;

    if (isReserved) {
      if (!scheduledDate || !scheduledTime) {
        alert('예약 발송 날짜와 시간을 선택해 주세요.');
        return;
      }
      if (new Date(formattedScheduledAt) <= new Date()) {
        alert('예약 발송 시간은 현재 시각 이후여야 합니다.');
        return;
      }
    }

    setIsLoading(true);
    try {
      // 수정 여부와 무관하게 항상 호출해야 한다 — 백엔드는 방금 만들어진 DRAFT를
      // 바로 발송/예약하지 못하게 막고(AI_INVALID_STATUS 409), 이 PATCH(edit)를 거쳐
      // REVIEWED가 된 메시지만 받는다. 사용자가 고친 문구도 여기서 함께 저장된다.
      await aiManagerApi.updateGeneratedMessage(messageId, {
        content: noticeText,
      });

      let response;
      if (isReserved) {
        response = await aiManagerApi.scheduleNotice(messageId, {
          scheduledAt: formattedScheduledAt,
        });
      } else {
        response = await aiManagerApi.createNotice(messageId);
      }

      if (response.data?.success) {
        alert(
          isReserved
            ? '공지 예약 발송이 성공적으로 등록되었습니다.'
            : '공지가 성공적으로 발송되었습니다.',
        );
        navigate(-1);
      }
    } catch (error) {
      console.error('Publish error:', error);
      alert(
        error.response?.data?.error?.message ||
          '공지 등록 중 오류가 발생했습니다.',
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggle = (key) => {
    setChannels((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  return (
    <PageWrapper>
      <Container>
        <BackButton onClick={() => navigate(-1)}>
          <ArrowLeft size={18} /> AI 매니저로 돌아가기
        </BackButton>

        <Banner>
          <BannerIconBox>
            <Sparkles size={20} />
          </BannerIconBox>
          <BannerText>
            <strong>AI 추천 요약</strong>
            <br />
            마케팅 매니저가 추천하는{' '}
            <strong>
              {NOTICE_TYPES.find((type) => type.value === noticeType)?.label}{' '}
              문구
            </strong>
            를 검토하고 발송하세요.
          </BannerText>
        </Banner>

        <MainGrid>
          <LeftColumn>
            <Card>
              <CardTitleGroup>
                <h3>공지 유형</h3>
                <p>만들 공지의 종류와 문구에 넣을 키워드를 정하세요</p>
              </CardTitleGroup>
              <TimeButtonGroup>
                {NOTICE_TYPES.map((type) => (
                  <TimeTabButton
                    key={type.value}
                    type="button"
                    $active={noticeType === type.value}
                    onClick={() => setNoticeType(type.value)}
                  >
                    {type.label}
                  </TimeTabButton>
                ))}
              </TimeButtonGroup>
              <PickerGroup>
                <PickerLabel>키워드 (선택)</PickerLabel>
                <StyledInput
                  type="text"
                  value={keyword}
                  maxLength={KEYWORD_MAX_LENGTH}
                  placeholder="예: 여름 냉면, 추석 연휴 휴무"
                  onChange={(e) => setKeyword(e.target.value)}
                />
              </PickerGroup>
            </Card>

            <Card>
              <CardHeader>
                <CardTitleGroup>
                  <h3>공지 문구</h3>
                  <p>자유롭게 수정한 뒤 등록할 수 있어요</p>
                </CardTitleGroup>
                {/* 문구 생성/재생성 버튼 이벤트 연결 */}
                <RegenerateButton
                  onClick={() => handleGenerateNoticeDraft(false)}
                  disabled={isLoading}
                >
                  <RotateCw
                    size={14}
                    className={isLoading ? 'spin' : ''}
                  />
                  문구 다시 생성
                </RegenerateButton>
              </CardHeader>

              <AiMessageBox>
                <AiTag>
                  <Sparkles size={14} /> AI 생성 문구 · 발랄한 톤
                </AiTag>
                <MessageTextArea
                  rows={4}
                  maxLength={NOTICE_TEXT_MAX_LENGTH}
                  value={noticeText}
                  onChange={(e) => setNoticeText(e.target.value)}
                />
              </AiMessageBox>
              <TextMeta>{noticeText.length}자 · 수정 가능</TextMeta>
            </Card>

            <Card>
              <CardTitleGroup>
                <h3>발송 채널</h3>
                <p>공지를 내보낼 채널을 선택하세요</p>
              </CardTitleGroup>

              <ChannelList>
                <ChannelItem $active={channels.KAKAO_ALERT}>
                  <ChannelLeft>
                    <ChannelIconBox $active={channels.KAKAO_ALERT}>
                      <MessageSquare size={20} />
                    </ChannelIconBox>
                    <ChannelInfo>
                      <h4>알림톡</h4>
                      <p>카카오 알림톡으로 발송</p>
                    </ChannelInfo>
                  </ChannelLeft>
                  <ToggleSwitch>
                    <input
                      type="checkbox"
                      checked={channels.KAKAO_ALERT}
                      onChange={() => handleToggle('KAKAO_ALERT')}
                    />
                    <span />
                  </ToggleSwitch>
                </ChannelItem>

                <ChannelItem $active={channels.APP_PUSH}>
                  <ChannelLeft>
                    <ChannelIconBox $active={channels.APP_PUSH}>
                      <Bell size={20} />
                    </ChannelIconBox>
                    <ChannelInfo>
                      <h4>앱 푸시</h4>
                      <p>이음 앱 푸시 알림</p>
                    </ChannelInfo>
                  </ChannelLeft>
                  <ToggleSwitch>
                    <input
                      type="checkbox"
                      checked={channels.APP_PUSH}
                      onChange={() => handleToggle('APP_PUSH')}
                    />
                    <span />
                  </ToggleSwitch>
                </ChannelItem>

                <ChannelItem $active={channels.STORE_NOTICE}>
                  <ChannelLeft>
                    <ChannelIconBox $active={channels.STORE_NOTICE}>
                      <Store size={20} />
                    </ChannelIconBox>
                    <ChannelInfo>
                      <h4>상점 공지 게시판</h4>
                      <p>매장 페이지 상단 노출</p>
                    </ChannelInfo>
                  </ChannelLeft>
                  <ToggleSwitch>
                    <input
                      type="checkbox"
                      checked={channels.STORE_NOTICE}
                      onChange={() => handleToggle('STORE_NOTICE')}
                    />
                    <span />
                  </ToggleSwitch>
                </ChannelItem>
              </ChannelList>
            </Card>

            <Card>
              <CardTitleGroup>
                <h3>발송 시간</h3>
              </CardTitleGroup>
              <TimeButtonGroup>
                <TimeTabButton
                  $active={sendType === 'IMMEDIATE'}
                  onClick={() => setSendType('IMMEDIATE')}
                >
                  즉시 발송
                </TimeTabButton>
                <TimeTabButton
                  $active={sendType === 'RESERVED'}
                  onClick={() => setSendType('RESERVED')}
                >
                  예약 발송
                </TimeTabButton>
              </TimeButtonGroup>

              {sendType === 'RESERVED' && (
                <PickerContainer>
                  <PickerGroup>
                    <PickerLabel>발송 날짜</PickerLabel>
                    <InputWrapper>
                      <StyledInput
                        type="date"
                        min={getTodayString()}
                        value={scheduledDate}
                        onChange={(e) => setScheduledDate(e.target.value)}
                      />
                      <CalendarIcon
                        size={18}
                        className="icon"
                      />
                    </InputWrapper>
                  </PickerGroup>

                  <PickerGroup>
                    <PickerLabel>발송 시간</PickerLabel>
                    <InputWrapper>
                      <StyledInput
                        type="time"
                        value={scheduledTime}
                        onChange={(e) => setScheduledTime(e.target.value)}
                      />
                      <Clock
                        size={18}
                        className="icon"
                      />
                    </InputWrapper>
                  </PickerGroup>
                </PickerContainer>
              )}
            </Card>
          </LeftColumn>

          <RightColumn>
            <SummaryCard>
              <ReachLabel>예상 도달</ReachLabel>
              <ReachCount>
                {estimatedReach}
                <span>명</span>
              </ReachCount>
              <ReachSub>선택한 채널 기준 추정치</ReachSub>
            </SummaryCard>

            <SubmitButton
              onClick={handlePublishNotice}
              disabled={isLoading}
            >
              <Send size={16} /> 공지 등록하기
            </SubmitButton>

            <CancelTextButton onClick={() => navigate(-1)}>
              취소
            </CancelTextButton>
          </RightColumn>
        </MainGrid>
      </Container>
    </PageWrapper>
  );
}

// (Styled Components 정의부는 이전과 동일)
const PageWrapper = styled.div`
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 32px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  &:hover {
    color: #111827;
  }
`;

const Banner = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 16px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const BannerIconBox = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background-color: #d1fae5;
  color: #059669;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const BannerText = styled.div`
  font-size: 14px;
  color: #374151;
  strong {
    color: #111827;
    font-weight: 700;
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  align-items: start;
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
  gap: 12px;
  position: sticky;
  top: 24px;
`;

const Card = styled.div`
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const CardTitleGroup = styled.div`
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  p {
    font-size: 13px;
    color: #9ca3af;
    margin: 4px 0 0;
  }
`;

const RegenerateButton = styled.button`
  background: none;
  border: none;
  color: #059669;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;

  .spin {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const AiMessageBox = styled.div`
  background-color: #f0fdf4;
  border: 1px dashed #86efac;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const AiTag = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #166534;
`;

const MessageTextArea = styled.textarea`
  width: 100%;
  background: transparent;
  border: none;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  color: #1f2937;
  font-family: inherit;
  &:focus {
    outline: none;
  }
`;

const TextMeta = styled.span`
  font-size: 12px;
  color: #9ca3af;
`;

const ChannelList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ChannelItem = styled.div`
  background-color: ${(props) => (props.$active ? '#f0fdf4' : '#f8fafc')};
  border: 1px solid ${(props) => (props.$active ? '#bbf7d0' : '#f1f5f9')};
  border-radius: 16px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.2s;
`;

const ChannelLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
`;

const ChannelIconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background-color: #ffffff;
  color: ${(props) => (props.$active ? '#16a34a' : '#9ca3af')};
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
`;

const ChannelInfo = styled.div`
  h4 {
    font-size: 15px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  p {
    font-size: 12px;
    color: #9ca3af;
    margin: 2px 0 0;
  }
`;

const ToggleSwitch = styled.label`
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
  input {
    opacity: 0;
    width: 0;
    height: 0;
  }
  span {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #cbd5e1;
    transition: 0.3s;
    border-radius: 24px;
    &:before {
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
  }
  input:checked + span {
    background-color: #41b37d;
  }
  input:checked + span:before {
    transform: translateX(20px);
  }
`;

const TimeButtonGroup = styled.div`
  display: flex;
  background-color: #f1f5f9;
  border-radius: 12px;
  padding: 4px;
  gap: 4px;
`;

const TimeTabButton = styled.button`
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background-color: ${(props) => (props.$active ? '#ffffff' : 'transparent')};
  color: ${(props) => (props.$active ? '#111827' : '#64748b')};
  box-shadow: ${(props) =>
    props.$active ? '0 1px 3px rgba(0, 0, 0, 0.1)' : 'none'};
  transition: all 0.2s;
`;

const PickerContainer = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
`;

const PickerGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const PickerLabel = styled.span`
  font-size: 13px;
  font-weight: 600;
  color: #374151;
`;

const InputWrapper = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  .icon {
    position: absolute;
    right: 14px;
    color: #374151;
    pointer-events: none;
  }
`;

const StyledInput = styled.input`
  width: 100%;
  height: 48px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 0 16px;
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
  background-color: #ffffff;
  font-family: inherit;
  &::-webkit-calendar-picker-indicator {
    opacity: 0;
    cursor: pointer;
    width: 100%;
    height: 100%;
    position: absolute;
    top: 0;
    left: 0;
  }
  &:focus {
    outline: none;
    border-color: #41b37d;
  }
`;

const SummaryCard = styled.div`
  background-color: #235d43;
  color: #ffffff;
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ReachLabel = styled.span`
  font-size: 13px;
  opacity: 0.8;
`;

const ReachCount = styled.div`
  font-size: 36px;
  font-weight: 800;
  span {
    font-size: 22px;
    font-weight: 600;
    margin-left: 4px;
  }
`;

const ReachSub = styled.p`
  font-size: 12px;
  opacity: 0.6;
  margin: 0;
`;

const SubmitButton = styled.button`
  width: 100%;
  background-color: #41b37d;
  color: #ffffff;
  border: none;
  border-radius: 14px;
  padding: 16px;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const CancelTextButton = styled.button`
  background: #f1f5f9;
  border: none;
  color: #6b7280;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
  padding: 16px;
`;
