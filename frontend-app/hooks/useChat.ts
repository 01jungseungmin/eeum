import { useState, useEffect, useRef, useCallback } from 'react';
import { Platform } from 'react-native';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/secureStore';
import * as encoding from 'text-encoding';

if (typeof global.TextEncoder === 'undefined') {
  global.TextEncoder = encoding.TextEncoder;
  global.TextDecoder = encoding.TextDecoder;
}

/**
 * React Native의 WebSocket 구현을 위한 우회 옵션이다. 브라우저에는 적용하면 안 된다.
 *
 * appendMissingNULLonIncoming은 수신 프레임 끝에 NULL을 덧붙이는데, 브라우저는 서버가
 * 보낸 정상적인 NULL 종료 프레임을 그대로 받으므로 프레임이 깨져 구독 콜백이 호출되지
 * 않는다. 연결과 전송은 멀쩡해서, 메시지를 보내면 서버에는 저장되는데 화면에는 안 뜨고
 * 방을 나갔다 들어와야(REST 재조회) 보이는 증상이 된다.
 */
const NATIVE_WS_FRAME_OPTIONS = Platform.OS === 'web'
  ? {}
  : { forceBinaryWSFrames: true, appendMissingNULLonIncoming: true };

const BASE_URL = process.env.EXPO_PUBLIC_API_URL || '';

/**
 * 웹소켓 주소는 API 주소에서 파생하되 /api 접두사를 떼야 한다.
 *
 * 운영은 https://eeum.life/api 로 프록시되지만 웹소켓은 그 밖의
 * wss://eeum.life/ws 다. BASE_URL 뒤에 그대로 /ws를 붙이면 /api/ws 로 나가는데,
 * Nginx에서 Upgrade·Connection 헤더를 전달하도록 잡아둔 건 /ws location이라
 * 핸드셰이크가 실패한다. 개발(http://host:8080)은 뗄 접두사가 없어 그대로 동작한다.
 */
const WEBSOCKET_URL =
  process.env.EXPO_PUBLIC_WS_URL ||
  BASE_URL.replace(/\/api\/?$/, '').replace(/^http/, 'ws') + '/ws';

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
        ...NATIVE_WS_FRAME_OPTIONS,

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