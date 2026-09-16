# 채팅 동시성 — 알려진 경쟁 시나리오

`concurrency-auditor`가 채팅 도메인 감사 시 확인하는 최소 점검 목록이다.
이 목록은 하한선일 뿐이며, 목록 밖 시나리오도 코드에서 도출해야 한다.

## 점검 항목

- 같은 참여자 조합의 PRIVATE 채팅방 중복 생성 (동시 생성 시에도 방 1개만)
- 같은 사용자의 중복 참여자 row 생성
- clientMessageId 기반 메시지 중복 전송 방지 (Redis TTL과 중복 요청 처리 흐름 확인)
- 읽음 처리와 unread count 감소 경쟁
- 메시지 삭제와 브로드캐스트 타이밍
- lastMessageAt 갱신 lost update 가능성

## 권장 방어

- DB Unique 제약 + 참여자 조합 hash
- Redis SET NX / Redis Lock
- atomic unread decrement
- AFTER_COMMIT 이벤트 브로드캐스트

## 대응 통합 테스트

- IT-CHAT-001: 동일 참여자 채팅방 동시 생성 → 1개만 생성
- IT-CHAT-002: 동일 clientMessageId 메시지 중복 전송 → 1회만 저장
