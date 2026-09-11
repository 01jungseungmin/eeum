import React, { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Info,
  Sparkles,
  Loader2,
  ExternalLink,
  AlertCircle,
  HelpCircle,
} from 'lucide-react';

// API 및 모달 경로
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

// 상수로 분리한 데이터 (.jsx)
import {
  INITIAL_CHAT_MESSAGES,
  ACTION_ROUTES,
} from '../../../constants/aiChatConstants';

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  margin-bottom: 16px;
  width: fit-content;

  &:hover {
    color: #111827;
  }
`;

const InfoBanner = styled.div`
  background-color: #f0fdf4;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #166534;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
`;

const ChipScrollContainer = styled.div`
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 12px;
  margin-bottom: 16px;
  flex-shrink: 0;

  &::-webkit-scrollbar {
    display: none;
  }
`;

const ChipButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 8px 14px;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const ChatMessageArea = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const MessageRow = styled.div`
  display: flex;
  gap: 12px;
  justify-content: ${(props) => (props.$isUser ? 'flex-end' : 'flex-start')};
  align-items: flex-start;
`;

const AiAvatar = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background-color: #34d399;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const MessageBubble = styled.div`
  max-width: 65%;
  background-color: ${(props) => (props.$isUser ? '#374151' : '#ffffff')};
  color: ${(props) => (props.$isUser ? '#ffffff' : '#1f2937')};
  border: ${(props) => (props.$isUser ? 'none' : '1px solid #f3f4f6')};
  box-shadow: ${(props) =>
    props.$isUser ? 'none' : '0 2px 8px rgba(0, 0, 0, 0.04)'};
  border-radius: 16px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ActionButtonGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px dashed #e5e7eb;
`;

const ActionButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background-color: #f0fdf4;
  color: #166534;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #dcfce7;
    border-color: #86efac;
  }
`;

const OutOfScopeBadge = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #d97706;
  background-color: #fef3c7;
  padding: 4px 8px;
  border-radius: 6px;
  width: fit-content;
  font-weight: 600;
`;

const NoticeText = styled.p`
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  margin: 16px 0 0 0;
`;

export default function AiChatPage() {
  const navigate = useNavigate();
  const chatEndRef = useRef(null);

  const [isLoading, setIsLoading] = useState(false);
  const [quickQuestions, setQuickQuestions] = useState([]); // API 질문 목록 상태
  const [messages, setMessages] = useState(INITIAL_CHAT_MESSAGES);

  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [upgradeErrorMessage, setUpgradeErrorMessage] = useState('');

  // 고정 추천 질문 목록 조회
  useEffect(() => {
    const fetchQuickQuestions = async () => {
      try {
        const response = await aiManagerApi.getQuickQuestions();
        if (response.data?.success) {
          setQuickQuestions(response.data.data || []);
        }
      } catch (error) {
        console.error('추천 질문 조회 실패:', error);
      }
    };

    fetchQuickQuestions();
  }, []);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  // 액션 버튼 이동 처리
  const handleActionClick = (actionType) => {
    const targetRoute = ACTION_ROUTES[actionType];
    if (targetRoute) {
      navigate(targetRoute);
    } else {
      console.warn('미정의된 ActionType:', actionType);
    }
  };

  // 질문 칩 선택 시 API 전송
  const handleSend = async (qItem) => {
    if (isLoading || !qItem) return;

    const payload = {
      quickQuestionId: qItem.id,
      text: qItem.question,
    };

    const userMsg = {
      id: Date.now(),
      sender: 'user',
      text: qItem.question,
    };
    setMessages((prev) => [...prev, userMsg]);
    setIsLoading(true);

    try {
      const response = await aiManagerApi.sendChatMessage(payload);

      if (response.data?.success) {
        const resData = response.data.data;

        const aiMsg = {
          id: Date.now() + 1,
          sender: 'ai',
          text: resData?.text || '답변이 완료되었습니다.',
          actions: resData?.actions || [],
          outOfScope: resData?.outOfScope || false,
        };
        setMessages((prev) => [...prev, aiMsg]);
      }
    } catch (error) {
      const errResponse = error.response?.data;

      if (errResponse?.error?.code === 'AI_001') {
        setUpgradeErrorMessage(errResponse.error.message);
        setIsUpgradeModalOpen(true);
      } else {
        setMessages((prev) => [
          ...prev,
          {
            id: Date.now() + 1,
            sender: 'ai',
            text:
              errResponse?.error?.message ||
              '메시지 전송 중 오류가 발생했습니다.',
          },
        ]);
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <PageContainer>
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      <InfoBanner>
        <Info size={16} />
        <span>
          리뷰·공지·이벤트·고객 메시지·문의 답변·에너지·안전 범위 안에서
          도와드려요
        </span>
      </InfoBanner>

      {/* API로 로드한 질문 칩 렌더링 */}
      <ChipScrollContainer>
        {quickQuestions.map((q) => (
          <ChipButton
            key={q.id}
            onClick={() => handleSend(q)}
            disabled={isLoading}
          >
            {q.generative ? (
              <Sparkles
                size={14}
                color="#10b981"
              />
            ) : (
              <HelpCircle
                size={14}
                color="#6b7280"
              />
            )}
            {q.question}
          </ChipButton>
        ))}
      </ChipScrollContainer>

      <ChatMessageArea>
        {messages.map((msg) => (
          <MessageRow
            key={msg.id}
            $isUser={msg.sender === 'user'}
          >
            {msg.sender === 'ai' && (
              <AiAvatar>
                <Sparkles size={20} />
              </AiAvatar>
            )}
            <MessageBubble $isUser={msg.sender === 'user'}>
              {msg.outOfScope && (
                <OutOfScopeBadge>
                  <AlertCircle size={14} /> 답변 범위 외 질문
                </OutOfScopeBadge>
              )}

              <div>{msg.text}</div>

              {msg.actions && msg.actions.length > 0 && (
                <ActionButtonGroup>
                  {msg.actions.map((act, index) => (
                    <ActionButton
                      key={index}
                      onClick={() => handleActionClick(act.actionType)}
                    >
                      {act.label} <ExternalLink size={14} />
                    </ActionButton>
                  ))}
                </ActionButtonGroup>
              )}
            </MessageBubble>
          </MessageRow>
        ))}

        {isLoading && (
          <MessageRow $isUser={false}>
            <AiAvatar>
              <Sparkles size={20} />
            </AiAvatar>
            <MessageBubble $isUser={false}>
              <Loader2
                size={18}
                style={{ animation: 'spin 1s linear infinite' }}
              />
            </MessageBubble>
          </MessageRow>
        )}

        <div ref={chatEndRef} />
      </ChatMessageArea>

      <NoticeText>
        저는 이음 안에서의 가게 운영, 고객 응대, 공지, 이벤트, 리뷰, 예약, 안전
        점검과 관련된 내용만 도와드릴 수 있어요.
      </NoticeText>

      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => setIsUpgradeModalOpen(false)}
        errorMessage={upgradeErrorMessage}
      />
    </PageContainer>
  );
}
