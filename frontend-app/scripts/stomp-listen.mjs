// 구독만 열고 기다린다. Redis에 직접 발행한 메시지가 STOMP 구독자까지 오는지 확인용.
//
//   cd frontend-app && node scripts/stomp-listen.mjs [roomId] [초]
//
// 이 스크립트가 도는 동안 서버에서:
//   docker exec eeum-redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning \
//     PUBLISH realtime:stomp "{\"destination\":\"/sub/chat/rooms/910001\",\"payloadJson\":\"{\\\"content\\\":\\\"redis-direct\\\"}\"}"'
//
// 잡히면  → Redis -> 구독자 -> 브로커 구간은 정상. 발행 단계(이벤트/비동기)가 문제다.
// 안 잡히면 → 구독자가 채널에 붙어 있어도 실제 전달이 안 되는 것이다.

import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

const API = process.env.PROBE_API ?? 'https://eeum.life/api';
const WS = process.env.PROBE_WS ?? 'wss://eeum.life/ws';
const ROOM = Number(process.argv[2] ?? 910001);
const SECONDS = Number(process.argv[3] ?? 60);

const login = await fetch(`${API}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: process.env.PROBE_EMAIL ?? 'kc467229+demo@gmail.com',
    password: process.env.PROBE_PASSWORD ?? 'EeumDemo2026!',
  }),
});
const token = (await login.json()).data.accessToken;

let received = 0;

const client = new Client({
  webSocketFactory: () => new WebSocket(WS),
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 0,
  onConnect: () => {
    client.subscribe(`/sub/chat/rooms/${ROOM}`, (m) => {
      received += 1;
      console.log(`[${new Date().toISOString().slice(11, 19)}] << 수신: ${m.body.slice(0, 200)}`);
    });
    console.log(`구독 시작 — /sub/chat/rooms/${ROOM}  (${SECONDS}초 대기)`);
    console.log('지금 서버에서 Redis PUBLISH 명령을 실행하세요.');
  },
  onStompError: (frame) => console.log('STOMP 에러:', frame.headers['message']),
  onWebSocketError: (event) => console.log('WS 에러:', event?.message ?? String(event)),
});

client.activate();

setTimeout(async () => {
  console.log(`\n=== 대기 종료: 수신 ${received}건 ===`);
  await client.deactivate();
  process.exit(received > 0 ? 0 : 2);
}, SECONDS * 1000);
