// 브라우저 없이 채팅 실시간 경로를 검증한다.
//
//   cd frontend-app && node scripts/stomp-probe.mjs [roomId]
//
// 세 경로를 구분해서 찍는다. 어디까지 살아 있는지로 원인을 좁힐 수 있다.
//
//   타이핑 / 읽음 — 컨트롤러가 messagingTemplate.convertAndSend로 직접 브로드캐스트한다.
//                   받으면 WebSocket 연결·STOMP 구독·인메모리 브로커·CORS가 전부 정상이다.
//   메시지        — ChatMessageBroadcastEvent -> @TransactionalEventListener(AFTER_COMMIT)
//                   -> RealtimeRelayPublisher -> Redis Pub/Sub -> RealtimeRelaySubscriber
//                   -> convertAndSend. 위 둘은 오는데 이것만 안 오면 이 중계 구간이 끊긴 것이다.
//
// 발행·수신 양쪽 실패를 모두 log.warn으로 삼키므로(원 트랜잭션을 되돌리지 않기 위해)
// 클라이언트에는 아무 에러도 오지 않는다. 서버 로그에서 다음을 찾아야 한다.
//   "실시간 중계 발행 실패"      RealtimeRelayPublisher  — Redis 발행 단계
//   "실시간 중계 수신 처리 실패"  RealtimeRelaySubscriber — 수신·역직렬화 단계
//   둘 다 없으면 이벤트 자체가 발행되지 않은 것이다(AFTER_COMMIT 미발화 등).

import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

const API = process.env.PROBE_API ?? 'https://eeum.life/api';
const WS = process.env.PROBE_WS ?? 'wss://eeum.life/ws';
const EMAIL = process.env.PROBE_EMAIL ?? 'kc467229+demo@gmail.com';
const PASSWORD = process.env.PROBE_PASSWORD ?? 'EeumDemo2026!';
const ROOM = Number(process.argv[2] ?? 910001);

const login = await fetch(`${API}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
});
const token = (await login.json()).data.accessToken;
console.log(`로그인 OK  (room=${ROOM})`);

const counts = { message: 0, typing: 0, read: 0 };
const marker = `probe-${Date.now()}`;

const client = new Client({
  webSocketFactory: () => new WebSocket(WS),
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 0,

  onConnect: () => {
    console.log('STOMP CONNECT 성공');
    client.subscribe(`/sub/chat/rooms/${ROOM}`, () => {
      counts.message += 1;
      console.log('  << 메시지 수신');
    });
    client.subscribe(`/sub/chat/rooms/${ROOM}/typing`, () => {
      counts.typing += 1;
      console.log('  << 타이핑 수신');
    });
    client.subscribe(`/sub/chat/rooms/${ROOM}/read`, () => {
      counts.read += 1;
      console.log('  << 읽음 수신');
    });
    client.subscribe('/user/sub/errors', (m) => console.log('  !! 서버 에러:', m.body.slice(0, 300)));

    const publish = (destination, body, delay) =>
      setTimeout(() => {
        console.log(`  >> 발행: ${destination}`);
        client.publish({ destination, body });
      }, delay);

    publish(`/pub/chat/rooms/${ROOM}/typing`, JSON.stringify({ typing: true }), 700);
    publish(`/pub/chat/rooms/${ROOM}/read`, '', 1400);
    publish(
      `/pub/chat/rooms/${ROOM}/messages`,
      JSON.stringify({ content: marker, clientMessageId: marker }),
      2100
    );
  },

  onStompError: (frame) => console.log('STOMP 에러:', frame.headers['message']),
  onWebSocketError: (event) => console.log('WS 에러:', event?.message ?? String(event)),
});

client.activate();

setTimeout(async () => {
  console.log('\n=== 결과 ===');
  console.log(`  타이핑(직접 브로드캐스트) : ${counts.typing}`);
  console.log(`  읽음  (직접 브로드캐스트) : ${counts.read}`);
  console.log(`  메시지(Redis 중계)        : ${counts.message}`);

  const saved = await fetch(`${API}/chat/rooms/${ROOM}/messages?size=5`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const items = (await saved.json())?.data?.content ?? [];
  console.log(`  메시지 DB 저장 여부       : ${items.some((m) => JSON.stringify(m).includes(marker))}`);

  await client.deactivate();
  process.exit(counts.message > 0 ? 0 : 2);
}, 8000);
