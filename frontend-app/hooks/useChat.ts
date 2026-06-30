import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/secureStore'; 
import * as encoding from 'text-encoding';

if (typeof global.TextEncoder === 'undefined') {
  global.TextEncoder = encoding.TextEncoder;
  global.TextDecoder = encoding.TextDecoder;
}

const BASE_URL = process.env.EXPO_PUBLIC_API_URL || '';
const WEBSOCKET_URL = BASE_URL.replace(/^http/, 'ws') + '/ws';

export const useChatStomp = (roomId: number) => {
  const clientRef = useRef<Client | null>(null);
  
  const [messages, setMessages] = useState<any[]>([]); 
  const [isConnected, setIsConnected] = useState(false);
  const [isOpponentTyping, setIsOpponentTyping] = useState(false); // 상대방 타이핑 상태

  useEffect(() => {
    const connectStomp = async () => {
      const token = await getAccessToken();

      const client = new Client({
        brokerURL: WEBSOCKET_URL, 
        connectHeaders: {
          Authorization: `Bearer ${token}`, 
        },
        reconnectDelay: 5000, 
        forceBinaryWSFrames: true,
        appendMissingNULLonIncoming: true,
        
        onConnect: () => {
          console.log('✅ STOMP 웹소켓 연결 성공!');
          setIsConnected(true);

          // 1. 일반 메시지 구독
          client.subscribe(`/sub/chat/rooms/${roomId}`, (message) => {
            const received = JSON.parse(message.body);
            console.log("💌 메시지 수신:", received);
            setMessages((prev) => [...prev, received]);
          });

          // 2. 읽음 이벤트 구독
          client.subscribe(`/sub/chat/rooms/${roomId}/read`, (message) => {
            console.log("👀 읽음 이벤트 수신:", JSON.parse(message.body));
            // 나중에 여기서 메시지 숫자 '1'을 지우는 로직을 추가할 수 있습니다.
          });

          // 3. 타이핑 이벤트 구독
          client.subscribe(`/sub/chat/rooms/${roomId}/typing`, (message) => {
            const typingData = JSON.parse(message.body);
            console.log("⌨️ 타이핑 이벤트 수신:", typingData);
            setIsOpponentTyping(typingData.typing);
          });

          // 4. 에러 구독
          client.subscribe(`/user/sub/errors`, (message) => {
            console.error("❌ 서버 에러 수신:", JSON.parse(message.body));
          });
        },

        onStompError: (frame) => console.error('STOMP 에러:', frame.headers['message']),
        onWebSocketError: (event) => console.error('웹소켓 통신 에러:', event),
        onWebSocketClose: () => setIsConnected(false)
      });

      client.activate();
      clientRef.current = client;
    };

    if (roomId) connectStomp();

    return () => {
      if (clientRef.current) clientRef.current.deactivate();
    };
  }, [roomId]);

  // 5. 메시지 발행 (전송)
  const sendMessage = useCallback((content: string) => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/pub/chat/rooms/${roomId}/messages`,
        body: JSON.stringify({
          content: content,
          clientMessageId: `msg-${Date.now()}` // 명세서 요구사항 반영
        }),
      });
    }
  }, [roomId, isConnected]);

  // 6. 읽음 처리 발행
  const sendReadReceipt = useCallback(() => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/pub/chat/rooms/${roomId}/read`,
        body: ""
      });
    }
  }, [roomId, isConnected]);

  // 7. 타이핑 이벤트 발행
  const sendTyping = useCallback((isTyping: boolean) => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/pub/chat/rooms/${roomId}/typing`,
        body: JSON.stringify({ typing: isTyping })
      });
    }
  }, [roomId, isConnected]);

  return { 
    messages, setMessages, isConnected, isOpponentTyping,
    sendMessage, sendReadReceipt, sendTyping
  };
};