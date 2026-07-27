import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../contexts/AuthContext';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const WEBSOCKET_URL = BASE_URL.replace(/^http/, 'ws') + '/ws';

export default function useChatSocket(roomId, onMessageReceived, myAccountId) {
  const [connected, setConnected] = useState(false);
  const [isOpponentTyping, setIsOpponentTyping] = useState(false);
  const stompClient = useRef(null);

  const { accessToken } = useAuth();

  useEffect(() => {
    if (!roomId || !accessToken) return;

    stompClient.current = new Client({
      brokerURL: WEBSOCKET_URL,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.current.onConnect = () => {
      setConnected(true);
      console.log(`✅ Room ${roomId} 웹소켓 연결 성공!`);

      // 1. 일반 메시지 구독
      stompClient.current.subscribe(`/sub/chat/rooms/${roomId}`, (message) => {
        const received = JSON.parse(message.body);

        // 💡 백엔드 구조에 맞춰 포맷팅 후 콜백 실행
        const formattedMsg = {
          messageId: received.messageId,
          roomId: received.roomId,
          senderAccountId: received.senderAccountId,
          senderName: received.senderName,
          senderProfileImageUrl: received.senderProfileImageUrl,
          content: received.content,
          imageUrl: received.imageUrl, // 💡 이미지 URL 추가 받아옴
          messageType: received.messageType, // 'TEXT' 또는 'IMAGE'
          isDeleted: received.deleted || false, // 💡 삭제 여부
          sentAt: received.sentAt,
          isMe: Number(received.senderAccountId) === Number(myAccountId), // 💡 내가 보낸 건지 판별
        };

        if (onMessageReceived) {
          onMessageReceived(formattedMsg);
        }
      });

      // 2. 타이핑 상태 구독
      stompClient.current.subscribe(
        `/sub/chat/rooms/${roomId}/typing`,
        (message) => {
          const typingData = JSON.parse(message.body);
          setIsOpponentTyping(typingData.typing);
        },
      );
    };

    stompClient.current.onDisconnect = () => setConnected(false);
    stompClient.current.activate();

    return () => {
      if (stompClient.current) stompClient.current.deactivate();
    };
  }, [roomId, accessToken, onMessageReceived, myAccountId]);

  // 텍스트 메시지 전송
  const sendMessage = useCallback(
    (content) => {
      if (!stompClient.current?.connected) return false;
      stompClient.current.publish({
        destination: `/pub/chat/rooms/${roomId}/messages`,
        body: JSON.stringify({
          content: content,
          clientMessageId: `msg-${Date.now()}`,
        }),
      });
      return true;
    },
    [roomId],
  );

  return {
    connected,
    isOpponentTyping,
    sendMessage,
  };
}
