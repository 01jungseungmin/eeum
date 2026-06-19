import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';

// API 호스트 주소 (실제 환경에 맞게 수정)
const API_HOST = process.env.EXPO_PUBLIC_API_URL?.replace('http://', '').replace('https://', '') || 'localhost:8080';

export const useChat = (roomId: string | number, token: string) => {
  const [messages, setMessages] = useState<any[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!roomId || !token) return;

    // 1. STOMP 클라이언트 생성
    const client = new Client({
      brokerURL: `ws://${API_HOST}/ws`, // 명세서의 Native WebSocket STOMP 연결
      connectHeaders: {
        Authorization: `Bearer ${token}`, // STOMP CONNECT Header에 토큰 전달
      },
      reconnectDelay: 5000, // 끊겼을 때 5초마다 재연결 시도
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      // debug: (str) => console.log('[STOMP Debug]:', str), // 필요시 주석 해제
    });

    // 2. 연결 성공 시 실행되는 함수
    client.onConnect = () => {
      console.log('✅ STOMP 연결 성공! Room ID:', roomId);
      setIsConnected(true);

      // 3. 채팅방 구독 (서버 -> 클라이언트)
      client.subscribe(`/sub/chat/rooms/${roomId}`, (message) => {
        if (message.body) {
          const newMessage = JSON.parse(message.body);
          // 받은 메시지를 상태 배열에 추가
          setMessages((prev) => [...prev, newMessage]);
        }
      });
    };

    // 에러 핸들링
    client.onStompError = (frame) => {
      console.error('🚨 STOMP 에러:', frame.headers['message']);
      console.error('상세 정보:', frame.body);
    };

    client.onWebSocketError = (event) => {
      console.error('🚨 WebSocket 에러:', event);
    };

    // 클라이언트 활성화 (연결 시작)
    client.activate();
    clientRef.current = client;

    // 컴포넌트 언마운트 시 연결 해제 (정리)
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        setIsConnected(false);
      }
    };
  }, [roomId, token]);

  // 4. 메시지 전송 함수 (클라이언트 -> 서버)
  const sendMessage = useCallback((content: string) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination: `/pub/chat/rooms/${roomId}/messages`,
        body: JSON.stringify({ content }), // 백엔드 명세에 맞춰 JSON 구조 변경 가능
      });
    } else {
      console.warn('❌ STOMP가 연결되어 있지 않습니다.');
    }
  }, [roomId]);

  return { messages, isConnected, sendMessage, setMessages };
};