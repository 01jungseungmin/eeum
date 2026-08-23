import React, { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Info,
  Sparkles,
  Tag,
  Megaphone,
  Star,
  Heart,
  MessageCircle,
  TrendingUp,
  ShieldCheck,
  Edit3,
  ArrowUp,
  Bot,
  User,
} from 'lucide-react';

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px); /* 전체 화면 높이에 맞춰 조절 */
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
`;

/* AI 매니저로 돌아가기 버튼 */
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

/* 안내 배너 */
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

/* 추천 질문 칩 가로 스크롤 영역 */
const ChipScrollContainer = styled.div`
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 12px;
  margin-bottom: 16px;

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
`;

/* 채팅 메시지 출력 영역 */
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
  max-width: 60%;
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
`;

/* 하단 입력 폼 영역 */
const InputAreaContainer = styled.div`
  margin-top: auto;
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
`;

const NoticeText = styled.span`
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
`;

const InputBoxWrapper = styled.form`
  width: 100%;
  background-color: #f3f4f6;
  border-radius: 28px;
  padding: 6px 8px 6px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const StyledInput = styled.input`
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 14px;
  color: #111827;

  &::placeholder {
    color: #9ca3af;
  }
`;

const SendButton = styled.button`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: ${(props) => (props.$hasText ? '#3b82f6' : '#34d399')};
  color: #ffffff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    opacity: 0.9;
  }
`;

// 추천 질문 데이터 리스트
const QUICK_QUESTIONS = [
  {
    icon: (
      <Tag
        size={14}
        color="#d97706"
      />
    ),
    text: '이번 주 이벤트 뭐 할까요?',
  },
  {
    icon: (
      <Megaphone
        size={14}
        color="#2563eb"
      />
    ),
    text: '오늘 공지 문구 써줘',
  },
  {
    icon: (
      <Star
        size={14}
        color="#ca8a04"
      />
    ),
    text: '우리 가게 리뷰 요약해줘',
  },
  {
    icon: (
      <Heart
        size={14}
        color="#e11d48"
      />
    ),
    text: '단골 고객 메시지 써줘',
  },
  {
    icon: (
      <MessageCircle
        size={14}
        color="#16a34a"
      />
    ),
    text: '미답변 문의 답변 초안 만들어줘',
  },
  {
    icon: (
      <TrendingUp
        size={14}
        color="#0284c7"
      />
    ),
    text: '이번 이벤트 성과 요약해줘',
  },
  {
    icon: (
      <ShieldCheck
        size={14}
        color="#16a34a"
      />
    ),
    text: '에너지·안전 점검 항목 알려줘',
  },
  {
    icon: (
      <Edit3
        size={14}
        color="#4b5563"
      />
    ),
    text: '답글 초안 써줘',
  },
];

export default function AiChatPage() {
  const navigate = useNavigate();
  const chatEndRef = useRef(null);
  const [inputText, setInputText] = useState('');
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'ai',
      text: '안녕하세요 사장님!\n리뷰 답글, 공지 문구, 이벤트 추천, 고객 메시지, 문의 답변을 도와드릴 수 있어요.\n아래 질문을 눌러보거나 직접 입력해 주세요 😊',
    },
  ]);

  // 자동 스크롤 하단 이동
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 메시지 전송 핸들러
  const handleSend = (textToSend) => {
    const query = textToSend || inputText;
    if (!query.trim()) return;

    // 사용자 메시지 추가
    const userMsg = { id: Date.now(), sender: 'user', text: query };
    setMessages((prev) => [...prev, userMsg]);
    setInputText('');

    // AI 모의 답변 생성 (1초 후)
    setTimeout(() => {
      const aiMsg = {
        id: Date.now() + 1,
        sender: 'ai',
        text: `'${query}'에 대해 요청하신 내용을 바탕으로 작성을 완료했습니다! 추가로 수정하고 싶으신 부분이 있다면 편하게 말씀해 주세요.`,
      };
      setMessages((prev) => [...prev, aiMsg]);
    }, 800);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    handleSend();
  };

  return (
    <PageContainer>
      {/* 돌아가기 버튼 */}
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      {/* 상단 안내 배너 */}
      <InfoBanner>
        <Info size={16} />
        <span>
          리뷰·공지·이벤트·고객 메시지·문의 답변·에너지·안전 범위 안에서
          도와드려요
        </span>
      </InfoBanner>

      {/* 추천 질문 칩 스크롤 바 */}
      <ChipScrollContainer>
        {QUICK_QUESTIONS.map((q, idx) => (
          <ChipButton
            key={idx}
            onClick={() => handleSend(q.text)}
          >
            {q.icon}
            {q.text}
          </ChipButton>
        ))}
      </ChipScrollContainer>

      {/* 채팅 메시지 출력 영역 */}
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
              {msg.text}
            </MessageBubble>
          </MessageRow>
        ))}
        <div ref={chatEndRef} />
      </ChatMessageArea>

      {/* 하단 입력창 */}
      <InputAreaContainer>
        <NoticeText>
          저는 이음 안에서의 가게 운영, 고객 응대, 공지, 이벤트, 리뷰, 예약,
          안전 점검과 관련된 내용만 도와드릴 수 있어요.
        </NoticeText>
        <InputBoxWrapper onSubmit={handleSubmit}>
          <StyledInput
            type="text"
            placeholder="리뷰·공지·이벤트·고객 메시지에 대해 물어보세요"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
          />
          <SendButton
            type="submit"
            $hasText={!!inputText.trim()}
          >
            <ArrowUp size={20} />
          </SendButton>
        </InputBoxWrapper>
      </InputAreaContainer>
    </PageContainer>
  );
}
