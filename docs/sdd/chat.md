# Chat 도메인 SDD

> 코드 경로: chat, chatRoom, chatParticipant, chatMessage, websocket
> 비고: REST 채팅 + STOMP WebSocket

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-7. 채팅

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자 간 실시간 소통을 지원하기 위한 채팅 기능으로, 채팅방 생성, 참여자 관리, 메시지 전송 및 읽음 처리 기능을 포함한다.

채팅방 정보는 ChatRoom 엔티티를 통해 관리되며, 채팅의 유형에 따라 1:1 채팅(PRIVATE), 그룹 채팅(GROUP), 지역 기반 채팅(GROUP_STREET)으로 구분된다. 각 채팅방은 특정 도메인과 연관될 수 있도록 refType과 refId를 통해 상점, 게시글 등 다양한 기능과 연결 가능하도록 설계하였다.

사용자는 채팅방에 참여할 수 있으며, 참여자 정보는 ChatParticipant 엔티티를 통해 관리된다. 각 참여자는 사용자(Account)와 채팅방(ChatRoom)에 연관되며, 마지막으로 메시지를 확인한 시간(lastReadTime)과 참여 상태(status)를 통해 읽음 처리 및 참여 상태를 관리할 수 있도록 설계하였다.

채팅 메시지는 ChatMessage 엔티티를 통해 관리되며, 각 메시지는 채팅방(ChatRoom)과 작성자(Account)와 연관된다. 메시지는 텍스트, 이미지, 시스템 메시지 등 다양한 유형을 지원하도록 messageType으로 구분되며, 전송 시간(timestamp)을 통해 메시지 상태를 관리한다.

또한 이미지 메시지를 지원하기 위해 imageUrl 속성을 포함하였으며, 이를 통해 다양한 형태의 콘텐츠 전송이 가능하도록 설계하였다.

이와 같은 구조를 통해 사용자 간 실시간 소통을 지원하며, 다양한 서비스 기능과 연계 가능한 유연한 채팅 시스템을 구현할 수 있도록 하였다.

> 참고: 본문에는 "전송 시간(timestamp)"이라는 표현이 사용되었으나 5.6절 Entity 명세에서는 해당 속성명이 `sentAt`으로 명명되어 있다. 원문 그대로 보존함.

## 5.6 Chat 도메인

### ● Entity

«Entity» ChatRoom
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
chatroomId | Long | PK, NOT NULL | 고유 식별자
createdBy | Long | FK, NOT NULL | 채팅방 생성자
type | ChatRoomType | NOT NULL | PRIVATE / GROUP / GROUP_STREET
refType | ChatRoomRefType | ENUM | Polymorphic 참조 타입 (TRADE / STORE / COMMUNITY 등)
refId | Long | nullable | 연관 도메인 ID (Polymorphic 참조, FK 아님)
name | String | nullable | 채팅방 이름 (GROUP 타입에서 사용)
isActive | boolean | NOT NULL, DEFAULT True | 채팅방 활성 상태 (GROUP 전체 퇴장 시 false)
lastMessageAt | LocalDateTime | nullable | 마지막 메시지 시각 (목록 정렬용)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ChatParticipant
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
chatparticipantId | Long | PK, NOT NULL | 고유 식별자
chatroomId | Long | FK, NOT NULL | 연관 채팅방
accountId | Long | FK, NOT NULL | 참여 사용자
lastReadTime | LocalDateTime | nullable | 마지막 읽은 시각 (안 읽은 메시지 수 계산)
status | ParticipantStatus | NOT NULL, DEFAULT ACTIVE | ACTIVE / LEFT
joinedAt | LocalDateTime | NOT NULL | 참여 시각
leftAt | LocalDateTime | nullable | 퇴장 시각
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ChatMessage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
chatmessageId | Long | PK, NOT NULL | 고유 식별자
chatroomId | Long | FK, NOT NULL | 연관 채팅방
accountId | Long | FK, NOT NULL | 발신자
content | String | nullable | 메시지 내용 (TEXT/SYSTEM 타입)
imageUrl | String | nullable | 이미지 URL (IMAGE 타입)
messageType | MessageType | NOT NULL | TEXT / IMAGE / SYSTEM
isDeleted | boolean | NOT NULL, DEFAULT false | 발신자 본인 삭제 여부 (Soft Delete)
sentAt | LocalDateTime | NOT NULL | 발신 시각 (인덱스 대상)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ChatRoom (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
updateLastMessageAt(LocalDateTime) | void | 마지막 메시지 시각 갱신
deactivate() | void | 채팅방 비활성화 (isActive=false, GROUP 전체 퇴장 시)
isPrivate() | boolean | PRIVATE 타입 여부
isGroup() | boolean | GROUP 또는 GROUP_STREET 타입 여부

«Entity» ChatParticipant (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
leave() | void | 퇴장 처리 (status=LEFT, leftAt 기록)
updateLastReadTime(LocalDateTime) | void | 마지막 읽은 시각 갱신
isActive() | boolean | ACTIVE 상태 여부

«Entity» ChatMessage (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
markDeleted() | void | Soft Delete (isDeleted=true)
isOwnedBy(accountId) | boolean | 발신자 본인 여부 확인
isDeletable(accountId) | boolean | 본인 발신 + 미삭제 상태일 때만 삭제 가능

### ● Controller

«Controller» ChatRoomController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /chat/rooms/private | ResponseEntity<ChatRoomResponseDto> | 1:1 채팅방 생성/조회 (refType+refId 기준 멱등)
POST | /chat/rooms/group | ResponseEntity<ChatRoomResponseDto> | GROUP 채팅방 생성 (참여자 다수 초대)
GET | /chat/rooms | ResponseEntity<Page<ChatRoomResponseDto>> | 내 채팅방 목록 (lastMessageAt 정렬, unreadCount 포함)
GET | /chat/rooms/{roomId} | ResponseEntity<ChatRoomDetailResponseDto> | 채팅방 상세 (참여자 목록 포함)
PATCH | /chat/rooms/{roomId}/leave | ResponseEntity<CommonResponseDto> | 채팅방 나가기 (PRIVATE→LEFT, GROUP 전체 퇴장→방 비활성)
POST | /chat/rooms/{roomId}/participants | ResponseEntity<CommonResponseDto> | GROUP 채팅방 참여자 초대
PATCH | /chat/rooms/{roomId}/read | ResponseEntity<CommonResponseDto> | 읽음 처리 (lastReadTime 갱신)

«Controller» ChatMessageController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /chat/rooms/{roomId}/messages | ResponseEntity<Page<ChatMessageResponseDto>> | 메시지 목록 조회 (커서 페이징, 최신→과거)
POST | /chat/rooms/{roomId}/messages | ResponseEntity<ChatMessageResponseDto> | 메시지 발송 (REST 폴백 — WebSocket 실패 시)
POST | /chat/rooms/{roomId}/messages/image | ResponseEntity<ChatMessageResponseDto> | 이미지 메시지 발송
DELETE | /chat/messages/{messageId} | ResponseEntity<CommonResponseDto> | 메시지 삭제 (본인만, Soft Delete)
GET | /chat/messages/unread/count | ResponseEntity<UnreadCountResponseDto> | 전체 안 읽은 메시지 수 (배지 표시용)

«Controller» ChatStompController (STOMP / WebSocket)
MAPPING | 메시지 매핑 | Payload | 설명
---|---|---|---
@MessageMapping | /chat/{roomId}/send | ChatMessageRequestDto | 메시지 전송 → /topic/chat/{roomId} 브로드캐스트
@MessageMapping | /chat/{roomId}/read | ChatReadRequestDto | 읽음 이벤트 전송 → /topic/chat/{roomId}/read
@MessageMapping | /chat/{roomId}/typing | TypingEventDto | 타이핑 인디케이터 → /topic/chat/{roomId}/typing
@SubscribeMapping | /topic/chat/{roomId} | - | 채팅방 구독 (인증·참여자 검증 인터셉터)

«Controller» AdminChatController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/chat/rooms | ResponseEntity<Page<ChatRoomResponseDto>> | 전체 채팅방 조회 (필터: 타입/기간)
GET | /admin/chat/rooms/{roomId}/messages | ResponseEntity<Page<ChatMessageResponseDto>> | 신고된 채팅방 메시지 조회
DELETE | /admin/chat/messages/{messageId} | ResponseEntity<CommonResponseDto> | 메시지 강제 삭제 (신고 처리)
DELETE | /admin/chat/rooms/{roomId} | ResponseEntity<CommonResponseDto> | 채팅방 강제 비활성화

### ● Service

«Service» ChatRoomService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createPrivateRoom(accountId, ChatRoomCreateRequestDto) | ChatRoomResponseDto | REQUIRED | RedisLock("chat:private:{refType}:{refId}") | 1:1 채팅방 생성/조회 (refType+refId 기준 멱등 — 기존 방 있으면 반환)
createGroupRoom(accountId, ChatRoomCreateRequestDto) | ChatRoomResponseDto | REQUIRED | - | GROUP 채팅방 생성 + 참여자 일괄 초대
getMyRooms(accountId, Pageable) | Page<ChatRoomResponseDto> | readOnly | - | 내 채팅방 목록 (ACTIVE 참여자 기준, lastMessageAt 내림차순, unreadCount 계산)
getRoomDetail(accountId, roomId) | ChatRoomDetailResponseDto | readOnly | Participant | 채팅방 상세 (참여자 목록 포함)
inviteParticipants(accountId, roomId, List<Long> accountIds) | void | REQUIRED | Participant + GroupOnly + Event | GROUP 채팅방 참여자 초대 + 입장 SYSTEM 메시지 발행
leaveRoom(accountId, roomId) | void | REQUIRED | Participant + Event | 퇴장 (status=LEFT, leftAt 기록). GROUP에서 전체 퇴장 시 isActive=false 처리 + 퇴장 SYSTEM 메시지
markRoomAsRead(accountId, roomId) | void | REQUIRED | Participant | lastReadTime 갱신 → 해당 방 unread 캐시 0으로 리셋 + 사용자 전체 unread에서 차감

«Service» ChatMessageService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
sendMessage(accountId, roomId, ChatMessageSendRequestDto) | ChatMessageResponseDto | REQUIRED | Participant + Event | 메시지 저장 → ChatRoom.lastMessageAt 갱신 → STOMP 브로드캐스트 + 알림 이벤트
sendImageMessage(accountId, roomId, MultipartFile) | ChatMessageResponseDto | REQUIRED | Participant + ImageService + Event | S3 업로드는 트랜잭션 외부에서 수행 (또는 Presigned URL 방식). DB 트랜잭션은 메시지 저장만 책임
getMessages(accountId, roomId, ChatMessageSearchDto, Pageable) | Page<ChatMessageResponseDto> | readOnly | Participant | 메시지 목록 (커서 페이징, isDeleted=true는 "삭제된 메시지" 표시)
deleteMessage(accountId, messageId) | void | REQUIRED | Ownership | 본인 메시지 Soft Delete → 브로드캐스트
countUnreadByAccountIdFromDb(Long accountId) | UnreadCountResponseDto | readOnly | - | 전체 안 읽은 메시지 수. Redis에서 unread:account:{accountId} 조회 → 캐시 미스 시 DB 쿼리 후 Redis 복구
broadcastTyping(accountId, roomId) | void | - | Participant | 타이핑 이벤트 STOMP 브로드캐스트 (DB 저장 X)

«Service» AdminChatService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAllRooms(Pageable, filter) | Page<ChatRoomResponseDto> | readOnly | Admin | 전체 채팅방 조회
getRoomMessages(roomId, Pageable) | Page<ChatMessageResponseDto> | readOnly | Admin | 신고된 채팅방 메시지 조회
forceDeleteMessage(messageId) | void | REQUIRED | Admin | 메시지 강제 삭제
forceDeactivateRoom(roomId) | void | REQUIRED | Admin | 채팅방 강제 비활성화

«Helper» ChatAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyParticipant(accountId, roomId) | ChatParticipant | 채팅방 참여자 (ACTIVE) 검증 → 실패 시 ForbiddenException
verifyMessageOwnership(accountId, messageId) | ChatMessage | 메시지 발신자 본인 검증
verifyRoomCreator(accountId, roomId) | ChatRoom | 채팅방 생성자 검증 (GROUP 관리 권한)
verifyGroupRoom(roomId) | ChatRoom | GROUP/GROUP_STREET 타입 검증 (PRIVATE 초대 차단)

### ● Repository

«Repository» ChatRoomRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long roomId) | Optional<ChatRoom> | 채팅방 조회
findByRefTypeAndRefIdAndType(ChatRoomRefType refType, Long refId, ChatRoomType type) | Optional<ChatRoom> | PRIVATE 채팅방 멱등 검증용 (거래/상점 1:1)
existsByRefTypeAndRefIdAndType(ChatRoomRefType refType, Long refId, ChatRoomType type) | boolean | PRIVATE 채팅방 존재 여부
countActiveParticipants(Long roomId) | Long | ACTIVE 참여자 수 (GROUP 전체 퇴장 판정용)
updateLastMessageAt(Long roomId, LocalDateTime sentAt) | int | @Modifying / 메시지 발송 시lastMessageAt 갱신(목록 정렬용)

«Repository» ChatRoomRepositoryCustom(QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
findMyRoomsWithUnreadCount(Long accountId, Pageable pageable) | Page<ChatRoomDto> | 내 채팅방 목록 (ChatParticipant JOIN + unreadCount 서브쿼리, lastMessageAt 정렬)
searchRoomsByAdmin(ChatRoomSearchDto, Pageable pageable) | Page<ChatRoom> | 관리자용 채팅방 검색 (타입/기간/생성자 필터)

«Repository» ChatParticipantRepository
메서드명 | 반환타입 | 설명
---|---|---
findByChatRoomIdAndAccountId(Long roomId, Long accountId) | Optional<ChatParticipant> | 본인 참여 여부 검증
existsByChatRoomIdAndAccountIdAndStatus(Long roomId, Long accountId, ParticipantStatus status) | boolean | ACTIVE 참여자 검증 (Helper용)
findAllByChatRoomId(Long roomId) | List<ChatParticipant> | 채팅방 참여자 목록
findAllByChatRoomIdAndStatus(Long roomId, ParticipantStatus status) | List<ChatParticipant> | ACTIVE 참여자만 조회 (브로드캐스트 대상)
findAllByAccountIdAndStatus(Long accountId, ParticipantStatus status, Pageable pageable) | Page<ChatParticipant> | 사용자의 ACTIVE 채팅방 목록
countByChatRoomIdAndStatus(Long roomId, ParticipantStatus status) | Long | ACTIVE 참여자 수 (GROUP 전체 퇴장 판정)
deleteAllByChatRoomId(Long roomId) | void | 채팅방 강제 삭제 시 CASCADE

«Repository» ChatMessageRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long messageId) | Optional<ChatMessage> | 메시지 조회
findByIdAndAccountId(Long messageId, Long accountId) | Optional<ChatMessage> | 본인 메시지 검증용
findAllByChatRoomIdOrderBySentAtDesc(Long roomId, Pageable pageable) | Page<ChatMessage> | 채팅방 메시지 목록 (최신순)
findAllByChatRoomIdAndSentAtBefore(Long roomId, LocalDateTime cursor, Pageable pageable) | List<ChatMessage> | 커서 페이징 (과거 메시지 로드)
countByChatRoomIdAndSentAtAfter(Long roomId, LocalDateTime lastReadTime) | Long | 채팅방별 안 읽은 메시지 수
findFirstByChatRoomIdOrderBySentAtDesc(Long roomId) | Optional<ChatMessage> | 마지막 메시지 (목록 화면 미리보기)
deleteAllByChatRoomId(Long roomId) | void | 채팅방 강제 삭제 시 CASCADE

### ● Component (Chat 관련, 원문 5.7절에 위치)

«Component» ChatNotificationEventListener
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
onMessageSent(ChatMessageSentEvent) | void | REQUIRED | @TransactionalEventListener(AFTER_COMMIT) + @Async | ACTIVE 참여자(발신자 제외)의 unread:account:{accountId} INCR + 방별 INCR + 비활성 참여자 푸시 발송 이벤트 발행
onParticipantInvited(ParticipantInvitedEvent) | void | REQUIRED | @TransactionalEventListener(AFTER_COMMIT) + @Async | GROUP 채팅방 입장 SYSTEM 메시지 발행
onParticipantLeft(ParticipantLeftEvent) | void | REQUIRED | @TransactionalEventListener(AFTER_COMMIT) + @Async | 퇴장 SYSTEM 메시지 발행 + GROUP 전체 퇴장 시 isActive=false

«Component» ChatUnreadReconcileScheduler
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
recalculateChatUnreadCounts() | void | - | @Scheduled(fixedRate = 300000) | 5분마다 — ChatMessageRepositoryCustom.countUnreadByAccountIdFromDb 호출하여 Redis 캐시 ↔ DB 비교, 불일치 시 보정

## 5.8 ENUM (Chat 관련)

«Enum» ChatRoomRefType
ENUM 값 | 설명
---|---
TRADE | 중고거래
STORE | 상점 문의
COMMUNITY | 커뮤니티
NONE | 일반 채팅

«Enum» ChatRoomType
ENUM 값 | 설명
---|---
PRIVATE | 1:1 채팅
GROUP | 일반 그룹
GROUP_STREET | 동네 단체 채팅

«Enum» MessageType
ENUM 값 | 설명
---|---
TEXT | 텍스트 메시지
IMAGE | 이미지 메시지
SYSTEM | 시스템 메시지

«Enum» ParticipantStatus
ENUM 값 | 설명
---|---
ACTIVE | 참여중
LEFT | 채팅방 퇴장

«Enum» AuditActionType (Chat 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
CHAT_MESSAGE_FORCE_DELETE | 채팅 메시지 삭제
CHAT_ROOM_FORCE_DEACTIVATE | 채팅방 비활성화

«Enum» NotificationType (Chat 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
CHAT_NEW_MESSAGE | 새 메시지
CHAT_INVITED | 채팅 초대

«Enum» NotificationRefType (Chat 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
CHAT_ROOM | 채팅방

«Enum» ReportRefType (Chat 관련, 중복 참조 — report.md 참조)
ENUM 값 | 설명
---|---
CHAT_MESSAGE | 채팅 메시지

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-21: 1:1 채팅방 생성 (멱등)
- DSEQ-22: 메시지 발송 (WebSocket)
- DSEQ-23: 메시지 읽음 처리

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● Chat 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### ChatRoom (채팅방)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
chat_room_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 채팅방 고유 식별자
created_by | bigint | FK(account), NOT NULL | 채팅방 생성자
type | varchar(20) | NOT NULL | ChatRoomType ENUM
ref_type | varchar(20) | nullable | ChatRoomRefType ENUM
ref_id | bigint | nullable | Polymorphic 참조ID (FK 아님)
name | varchar(100) | nullable | 채팅방 이름(GROUP 타입)
is_active | boolean | NOT NULL, DEFAULT true | 채팅방 활성 상태
last_message_at | datetime | nullable | 마지막 메시지 시각(목록 정렬용)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ChatParticipant (채팅 참여자)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
chat_participant_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 채팅 참여자 고유 식별자
chat_room_id | bigint | FK(chat_room), NOT NULL | 연관 채팅방
account_id | bigint | FK(account), NOT NULL | 참여 사용자
last_read_time | datetime | nullable | 마지막 읽은 시각(안 읽은 수 계산)
status | varchar(20) | NOT NULL, DEFAULT 'ACTIVE' | ParticipantStatus ENUM
joined_at | datetime | NOT NULL | 참여 시각
left_at | datetime | nullable | 퇴장 시각
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ChatMessage (채팅 메시지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
chat_message_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 채팅 메시지 고유 식별자
chat_room_id | bigint | FK(chat_room), NOT NULL | 연관 채팅방
account_id | bigint | FK(account), NOT NULL | 발신자
content | text | nullable | 메시지 내용(TEXT/SYSTEM 타입)
image_url | varchar(500) | nullable | 이미지URL (IMAGE 타입)
message_type | varchar(20) | NOT NULL | MessageType ENUM
is_deleted | boolean | NOT NULL, DEFAULT false | 발신자 본인 삭제 여부(Soft Delete)
sent_at | datetime | NOT NULL, INDEX | 발신 시각(인덱스 대상)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

## 중복 참조 (common.md에도 기재됨)

- Redis 분산 락 키: `chat:private:{refType}:{refId}` (TTL 5s, 1:1 채팅방 멱등 생성)는 `common.md` 6.1.2 참조
- 멱등성: (refType, refId, type) UNIQUE 기반 1:1 채팅방 생성 멱등성은 `common.md` 6.5.1 참조
- 캐싱: 전체/방별 채팅 unread 키(`unread:chat:{accountId}`, `unread:chat:{accountId}:room:{roomId}`)는 `common.md` 6.4.1 참조
- 트랜잭션 동기화 전략(AFTER_COMMIT + @Async)은 `common.md` 6.2.3, 6.2.4 참조
- ChatMessage Soft Delete 정책은 `common.md` 6.6.2, 6.6.4 참조
- 채팅 메시지 커서 페이징 전략은 `common.md` 6.11.4 참조
