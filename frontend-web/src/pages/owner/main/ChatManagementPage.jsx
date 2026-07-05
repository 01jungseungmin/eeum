import React, { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import { chatApi } from '../../../api/owner/chatApi';

// -------------------------------------------------------------
// 스타일 컴포넌트 영역 (기존 UI 유지)
// -------------------------------------------------------------
const PageContainer = styled.div`
  flex: 1;
  padding: 20px;
  background-color: #f8f9fa;
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  font-family: 'Noto Sans KR', sans-serif;
`;

const ChatWrapper = styled.div`
  flex: 1;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #eaeaea;
`;

const ChatHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background-color: #ffffff;
  border-bottom: 1px solid #eaeaea;
`;

const UserProfile = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const Avatar = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background-color: #42a574;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
  overflow: hidden;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const UserInfo = styled.div`
  display: flex;
  flex-direction: column;
`;

const UserName = styled.span`
  font-size: 16px;
  font-weight: bold;
  color: #333;
`;

const ShopBadge = styled.span`
  font-size: 12px;
  color: #888;
  margin-top: 3px;
`;

const StatusIndicator = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #42a574;
  font-weight: 500;

  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #42a574;
  }
`;

const MessageArea = styled.div`
  flex: 1;
  padding: 24px;
  background-color: #f8f9fa;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
`;

const MessageRow = styled.div`
  display: flex;
  justify-content: ${({ isMe }) => (isMe ? 'flex-end' : 'flex-start')};
  align-items: flex-end;
  gap: 8px;
`;

const BubbleWrap = styled.div`
  display: flex;
  align-items: flex-end;
  gap: 6px;
  max-width: 65%;
  flex-direction: ${({ isMe }) => (isMe ? 'row-reverse' : 'row')};
`;

const ChatBubble = styled.div`
  padding: 12px 18px;
  border-radius: ${({ isMe }) =>
    isMe ? '18px 18px 4px 18px' : '18px 18px 18px 4px'};
  background-color: ${({ isMe }) => (isMe ? '#42a574' : '#ffffff')};
  color: ${({ isMe }) => (isMe ? '#ffffff' : '#333333')};
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  white-space: pre-wrap;
  font-style: ${({ isDeleted }) => (isDeleted ? 'italic' : 'normal')};
  color: ${({ isDeleted, isMe }) =>
    isDeleted ? '#bbb' : isMe ? '#ffffff' : '#333333'};
`;

const TimeStamp = styled.span`
  font-size: 11px;
  color: #aaa;
  white-space: nowrap;
`;

const InputBarContainer = styled.div`
  padding: 20px 24px;
  background-color: #ffffff;
  border-top: 1px solid #eaeaea;
`;

const InputFieldWrapper = styled.div`
  display: flex;
  align-items: center;
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 28px;
  padding: 8px 10px 8px 20px;

  &:focus-within {
    border-color: #42a574;
    box-shadow: 0 0 0 1px #42a574;
  }
`;

const MessageInput = styled.input`
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: #333;
`;

const SendIconButton = styled.button`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #f1f3f5;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #42a574;
  transition: all 0.2s;

  &:hover {
    background-color: #42a574;
    color: #ffffff;
  }

  &:disabled {
    color: #ccc;
    cursor: not-allowed;
  }
`;

const LoadingText = styled.div`
  text-align: center;
  padding: 20px;
  color: #888;
`;

// -------------------------------------------------------------
// 컴포넌트 본문
// -------------------------------------------------------------
export default function ShopChatManagement() {
  // 개설된 상점 방 번호 (문의 관리 배너 등에서 받아온 ID 혹은 로컬스토리지를 연동합니다)
  const roomKey = localStorage.getItem('my_shop_room_id') || '1';
  const ROOM_ID = parseInt(roomKey, 10);

  // 사장님 본인 계정 ID (나/상대방 구별용)
  const MY_ACCOUNT_ID = 1004;

  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false); // 💡 메시지 전송 중 잠금 상태 State
  const scrollRef = useRef(null);

  const formatTime = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
  };

  // 1. 초기 메시지 목록 불러오기
  const loadChatMessages = async () => {
    try {
      const res = await chatApi.getMessages(ROOM_ID);
      if (res.data.success && res.data.data.content) {
        const sortedMessages = [...res.data.data.content].reverse();
        setMessages(sortedMessages);
      }
    } catch (error) {
      console.error('메시지 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadChatMessages();
  }, []);

  // 대화 목록 갱신 시 최하단 자동 스크롤
  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 💡 2. 텍스트 메시지 발송 핸들러
  const handleSendMessage = async () => {
    if (!inputValue.trim() || sending) return;

    setSending(true);

    // 고유한 클라이언트 메시지 아이디 생성 (UUID 규격 혹은 고유 문자열 대체)
    const clientMsgId = crypto.randomUUID
      ? crypto.randomUUID()
      : `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    try {
      const requestBody = {
        content: inputValue,
        clientMessageId: clientMsgId,
      };

      // 💡 Swagger 스펙 POST /chat/rooms/{roomId}/messages API 전송
      const res = await chatApi.sendMessage(ROOM_ID, requestBody);

      if (res.data.success && res.data.data) {
        // 백엔드에서 리턴한 가공된 정식 메시지 객체 취득
        const sentMessageData = res.data.data;

        // 기존 화면 메시지 배열의 끝에 추가해 줌으로써 즉각 화면 랜더링
        setMessages((prev) => [...prev, sentMessageData]);
        setInputValue(''); // 인풋창 초기화
      }
    } catch (error) {
      console.error('메시지 전송 실패:', error);
      alert('메시지 전송에 실패했습니다. 다시 시도해 주세요.');
    } finally {
      setSending(false);
    }
  };

  return (
    <PageContainer>
      <ChatWrapper>
        <ChatHeader>
          <UserProfile>
            <Avatar>이</Avatar>
            <UserInfo>
              <UserName>실시간 고객 문의 상담방</UserName>
              <ShopBadge>🏠 맛있는 반찬가게 · 마포구</ShopBadge>
            </UserInfo>
          </UserProfile>
          <StatusIndicator>실시간 연결됨</StatusIndicator>
        </ChatHeader>

        <MessageArea>
          {loading ? (
            <LoadingText>채팅 내역을 불러오는 중입니다...</LoadingText>
          ) : (
            messages.map((msg) => {
              const isMe = msg.senderAccountId === MY_ACCOUNT_ID;

              return (
                <MessageRow key={msg.messageId} isMe={isMe}>
                  {!isMe && (
                    <Avatar
                      style={{
                        width: '34px',
                        height: '34px',
                        fontSize: '13px',
                        marginRight: '4px',
                      }}
                    >
                      {msg.senderProfileImageUrl ? (
                        <img
                          src={msg.senderProfileImageUrl}
                          alt={msg.senderName}
                        />
                      ) : (
                        msg.senderName?.charAt(0) || '고'
                      )}
                    </Avatar>
                  )}
                  <BubbleWrap isMe={isMe}>
                    <ChatBubble isMe={isMe} isDeleted={msg.deleted}>
                      {msg.deleted ? '삭제된 메시지입니다' : msg.content}
                    </ChatBubble>
                    <TimeStamp>{formatTime(msg.sentAt)}</TimeStamp>
                  </BubbleWrap>
                </MessageRow>
              );
            })
          )}
          <div ref={scrollRef} />
        </MessageArea>

        <InputBarContainer>
          <InputFieldWrapper>
            <MessageInput
              placeholder={
                sending ? '전송 중...' : '고객에게 보낼 메시지를 입력하세요...'
              }
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
              disabled={sending} // 전송 중일 때는 일시 비활성화해 연타 방지
            />
            <SendIconButton
              onClick={handleSendMessage}
              disabled={!inputValue.trim() || sending}
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <line x1="22" y1="2" x2="11" y2="13"></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
            </SendIconButton>
          </InputFieldWrapper>
        </InputBarContainer>
      </ChatWrapper>
    </PageContainer>
  );
}
