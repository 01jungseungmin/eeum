# Notification 도메인 SDD

> 코드 경로: notification, notificationSettings, push, fcm, sse
> 비고: 알림, 알림 설정, 푸시

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-8. 알림

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 서비스 내에서 발생하는 주요 이벤트를 사용자에게 전달하기 위한 기능으로, 주문 상태 변경, 채팅 메시지 수신, 리뷰 작성, 공지사항 등 다양한 알림을 관리한다.

알림 정보는 Notification 엔티티를 통해 관리되며, 각 알림은 사용자(Account)와 연관되어 개별적으로 전달된다. 알림은 특정 도메인과 연결될 수 있도록 refType과 refId를 통해 주문, 채팅, 게시글, 리뷰 등 다양한 기능과 유연하게 연관되도록 설계하였다.

사용자는 자신에게 전달된 알림 목록을 조회할 수 있으며, 이를 통해 서비스 내에서 발생한 주요 이벤트를 실시간 또는 비동기적으로 확인할 수 있도록 하였다.
또한 알림은 이벤트 발생 시 자동으로 생성되도록 설계하여, 사용자 경험을 향상시키고 서비스의 반응성을 높일 수 있도록 하였다.

이와 같은 구조를 통해 다양한 기능에서 발생하는 이벤트를 통합적으로 관리하고, 사용자에게 필요한 정보를 적시에 전달할 수 있도록 설계하였다.

## 5.7 공통 도메인 내 Notification 명세 (원문 위치)

### ● Entity

«Entity» Notification
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
notificationId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 알림 수신자
type | NotificationType | NOT NULL | 알림 종류 ENUM
title | String | NOT NULL | 알림 제목 (푸시 표시용)
content | String | NOT NULL | 알림 본문
refType | NotificationRefType | ENUM | Polymorphic 참조 타입
refId | Long | nullable | Polymorphic 참조 ID (FK 아님, Service에서 검증)
linkUrl | String | nullable | 클라이언트 딥링크 URL
isRead | boolean | NOT NULL, DEFAULT false | 읽음 여부
readAt | LocalDateTime | nullable | 읽은 시각
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» NotificationSettings
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
notificationsettingsId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL, UNIQUE | 사용자
orderEnabled | boolean | NOT NULL, DEFAULT true | 주문/결제 알림 (필수)
reservationEnabled | boolean | NOT NULL, DEFAULT true | 예약 알림 (필수)
chatEnabled | boolean | NOT NULL, DEFAULT true | 채팅 알림
communityEnabled | boolean | NOT NULL, DEFAULT true | 커뮤니티 알림 (댓글/대댓글/좋아요)
storeReviewEnabled | boolean | NOT NULL, DEFAULT true | 상점/리뷰 알림
usedProductEnabled | boolean | NOT NULL, DEFAULT true | 중고거래 알림
systemEnabled | boolean | NOT NULL, DEFAULT true | 시스템 공지 (필수)
marketingEnabled | boolean | NOT NULL, DEFAULT false | 마케팅/이벤트 알림 (명시적 수신 동의 필요)
marketingAgreedAt | LocalDateTime | nullable | 마케팅 수신 동의 시각 (법적 증빙용)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Notification (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
markAsRead() | void | 읽음 처리 (isRead=true, readAt 기록)
isOwnedBy(accountId) | boolean | 본인 알림 여부
isUnread() | boolean | 안 읽음 여부

«Entity» NotificationSettings (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
isAllowed(NotificationType type) | boolean | 해당 타입 알림 수신 동의 여부 검증
agreeToMarketing() | void | 마케팅 수신 동의 (marketingEnabled=true, marketingAgreedAt 기록)
disagreeToMarketing() | void | 마케팅 수신 거부 (marketingEnabled=false, marketingAgreedAt=null)
toggleChatEnabled() | void | 채팅 알림 토글
toggleCommunityEnabled() | void | 커뮤니티 알림 토글
toggleStoreReviewEnabled() | void | 상점/리뷰 알림 토글
toggleUsedProductEnabled() | void | 중고거래 알림 토글

### ● Controller

«Controller» NotificationController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /notifications | ResponseEntity<Page<NotificationResponseDto>> | 내 알림 목록 (최신순, 페이징)
GET | /notifications/unread/count | ResponseEntity<UnreadCountResponseDto> | 안 읽은 알림 수 (배지)
GET | /notifications/unread | ResponseEntity<Page<UnreadNotificationResponseDto>> | 안 읽은 알림
PATCH | /notifications/{notificationId}/read | ResponseEntity<CommonResponseDto> | 알림 읽음 처리
PATCH | /notifications/read/all | ResponseEntity<CommonResponseDto> | 모두 읽음 처리
DELETE | /notifications/{notificationId} | ResponseEntity<CommonResponseDto> | 알림 삭제
DELETE | /notifications/all | ResponseEntity<CommonResponseDto> | 전체 삭제 (사용자가 알림함 비우기)

«Controller» NotificationSettingsController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /notifications/settings | ResponseEntity<NotificationSettingsResponseDto> | 내 알림 수신 설정
PATCH | /notifications/settings | ResponseEntity<NotificationSettingsResponseDto> | 카테고리별 ON/OFF 변경

«Controller» AdminNotificationController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /admin/notifications/system | ResponseEntity<CommonResponseDto> | 시스템 공지 발송 (전체 또는 필터 대상)
POST | /admin/notifications/event | ResponseEntity<CommonResponseDto> | 이벤트 알림 발송 (지역/연령대 등 타겟팅)
GET | /admin/notifications | ResponseEntity<Page<NotificationResponseDto>> | 발송 이력 조회 (감사용)

### ● Service

«Service» NotificationService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createNotification(NotificationCreateRequestDto) | NotificationResponseDto | REQUIRED | Internal + Event | 알림 허용 여부 검증 후 Notification 저장, Redis 카운트 증가 및 푸시 이벤트 발행
createNotificationsBatch(List<NotificationCreateRequestDto>) | List<NotificationResponseDto> | REQUIRED | Internal + Event | 다수 사용자 일괄 발송 (그룹 채팅, 시스템 공지 등)
getMyNotifications(accountId, Pageable) | Page<NotificationResponseDto> | readOnly | - | 내 알림 목록 (최신순)
getUnreadCount(accountId) | UnreadCountResponseDto | readOnly | RedisCache | 안 읽은 알림 수 (Redis 조회 → 캐시 미스 시 Db 호출 후 Redis 복구)
markAsRead(accountId, notificationId) | void | REQUIRED | Ownership + CacheEvict | 읽음 처리
markAllAsRead(accountId) | void | REQUIRED | CacheEvict | 모두 읽음 처리
deleteNotification(accountId, notificationId) | void | REQUIRED | Ownership + CacheEvict | 삭제
deleteAllByAccountId(accountId) | void | REQUIRED | CacheEvict | 전체 삭제 (회원 탈퇴 CASCADE)
deleteAllByRefTypeAndRefId(refType, refId) | void | REQUIRED | Internal | 대상 도메인 삭제 시 관련 알림 일괄 삭제

«Service» NotificationSettingsService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getMySettings(accountId) | NotificationSettingsResponseDto | readOnly | - | 내 알림 설정 조회
updateSettings(accountId, NotificationSettingsUpdateRequestDto) | NotificationSettingsResponseDto | REQUIRED | - | 카테고리별 ON/OFF 변경
isAllowedForAccount(accountId, NotificationType type) | boolean | readOnly | Internal | 발송 전 수신 동의 여부 검증

«Service» AdminNotificationService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
sendSystemNotice(SystemNoticeRequestDto) | void | REQUIRED | Admin + Async | 전체 또는 필터(지역/연령) 대상 시스템 공지
sendEventNotice(EventNoticeRequestDto) | void | REQUIRED | Admin + Async | 이벤트 알림 (마케팅 수신 동의자 한정)
getNotificationHistory(NotificationSearchDto, Pageable) | Page<NotificationResponseDto> | readOnly | Admin | 발송 이력 조회

«Helper» NotificationAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyNotificationOwnership(accountId, notificationId) | Notification | 본인 알림 검증
buildLinkUrl(refType, refId, extraParams) | String | refType별 딥링크 URL 자동 생성

### ● Interface

«interface» PushAdapter
메서드명 | 반환타입 | 설명
---|---|---
send(PushMessage message) | PushResult | 단건 푸시 발송
sendBatch(List<PushMessage> messages) | List<PushResult> | 배치 푸시 발송 (시스템 공지용, 최대 500건/요청)

### ● Component

«Component» NotificationCleanupScheduler
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
cleanupOldNotifications() | void | - | @Scheduled(cron = "0 0 3 * * *") | 매일 새벽 3시 — 6개월 이전 알림 일괄 삭제(NotificationService.deleteOldNotifications 호출)
recalculateUnreadCounts() | void | - | @Scheduled(fixedRate = 300000) | 5분마다 — Redis unread 캐시 ↔ DB 정합성 보정

«Component» FcmPushAdapter (구현체)
메서드명 | 반환타입 | 설명
---|---|---
send(PushMessage) | PushResult | FCM API 호출 (Android/iOS/Web 통합)
sendBatch(List<PushMessage>) | List<PushResult> | FCM 멀티캐스트 API 호출
handleInvalidToken(String fcmToken) | void | 만료/무효 토큰 처리 (Account.fcmToken 무효화)

### ● Repository

«Repository» NotificationRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long notificationId) | Optional<Notification> | 알림 조회
findByIdAndAccountId(Long notificationId, Long accountId) | Optional<Notification> | 본인 알림 검증용
findAllByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable) | Page<Notification> | 내 알림 목록 (최신순)
countByAccountIdAndIsReadFalse(Long accountId) | Long | 안 읽은 알림 수 (캐시 미스 시)
markAllAsReadByAccountId(@Param("accountId") Long accountId, @Param("now") LocalDateTime now) | int | @Modifying / 사용자의 모든 알림 일괄 읽음 처리(isRead=true, readAt=now)
deleteAllByAccountId(Long accountId) | void | 사용자 알림 일괄 삭제 (회원 탈퇴 CASCADE)
deleteAllByRefTypeAndRefId(NotificationRefType refType, Long refId) | void | 대상 도메인 삭제 시 CASCADE
deleteOldNotifications(LocalDateTime threshold) | int | 오래된 알림 정리 배치용 (예: 6개월 이전)

«Repository» NotificationRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchAdminNotifications(NotificationSearchDto, Pageable) | Page<Notification> | 관리자용 발송 이력 검색 (type/기간/대상자 필터)
countSentByTypeAndPeriod(NotificationType type, LocalDateTime from, LocalDateTime to) | Long | 통계용 — 기간별 type 발송 수
findEnabledNotificationsByAccountId(Long accountId, Pageable pageable) | Page<Notification> | NotificationSettings JOIN으로 수신 거부 카테고리 제외 조회

«Repository» NotificationSettingsRepository
메서드명 | 반환타입 | 설명
---|---|---
findByAccountId(Long accountId) | Optional<NotificationSettings> | 사용자 설정 조회 (없으면 기본값 사용)
existsByAccountId(Long accountId) | boolean | 설정 존재 여부

## 5.8 ENUM (Notification 관련)

«Enum» NotificationType (주문/결제 관련)
ENUM 값 | 설명
---|---
ORDER_CREATED | 주문 생성
ORDER_CONFIRMED | 주문 확정
ORDER_CANCELLED | 주문 취소
ORDER_REFUNDED | 환불 완료
PAYMENT_COMPLETED | 결제 완료
PAYMENT_FAILED | 결제 실패

«Enum» NotificationType (예약 관련)
ENUM 값 | 설명
---|---
RESERVATION_REQUESTED | 예약 요청
RESERVATION_CONFIRMED | 예약 확정
RESERVATION_REJECTED | 예약 거절
RESERVATION_CANCELLED | 예약 취소
RESERVATION_EXPIRED | 예약 만료

«Enum» NotificationType (사장승인/시스템/채팅/마케팅 관련)
ENUM 값 | 설명
---|---
CHAT_NEW_MESSAGE | 새 메시지
CHAT_INVITED | 채팅 초대
OWNER_APPROVED | 사장 승인 완료
OWNER_REJECTED | 사장 승인 거절
SYSTEM_NOTICE | 시스템 공지
SYSTEM_ANNOUNCEMENT | 시스템 안내

«Enum» NotificationType (마케팅 관련)
ENUM 값 | 설명
---|---
MARKETING_EVENT | 이벤트 알림
MARKETING_PROMOTION | 프로모션 알림

«Enum» NotificationType (리뷰/공지/거래/커뮤니티 관련)
ENUM 값 | 설명
---|---
STORE_REVIEW_NEW | 리뷰 등록
STORE_REVIEW_REPLY | 리뷰 답글
STORE_NOTICE_NEW | 공지 등록
USED_PRODUCT_RESERVED | 상품 예약됨
USED_PRODUCT_SOLD | 상품 판매 완료
USED_PRODUCT_REVIEW_NEW | 거래 리뷰 등록
COMMUNITY_COMMENT_NEW | 댓글 등록
COMMUNITY_REPLY_NEW | 대댓글 등록
COMMUNITY_POST_LIKE | 게시글 좋아요

> 참고: 원문 5.8절에는 NotificationType이 여러 개의 작은 표로 분산 기재되어 있으며(총 25종으로 9.3절 테이블 정의에 명시됨), 표 헤더("«Enum» CategoryType")가 잘못 반복 표기되어 있는 부분이 있다. 위 표들은 실제로는 모두 NotificationType ENUM 값으로 추정되며, 원문 그대로 구분하여 보존하였다.

«Enum» NotificationRefType
ENUM 값 | 설명
---|---
ORDER | 주문
PAYMENT | 결제
RESERVATION | 예약
CHAT_ROOM | 채팅방
STORE | 상점
STORE_REVIEW | 상점 리뷰
USED_PRODUCT | 중고상품
USED_PRODUCT_REVIEW | 중고 리뷰
COMMUNITY_POST | 게시글
COMMUNITY_COMMENT | 댓글
INQUIRY | 문의
OWNER_INFO | 사장 정보
SYSTEM | 시스템

«Enum» PushResult
ENUM 값 | 설명
---|---
SUCCESS | 전송 성공
FAILED | 전송 실패
INVALID_TOKEN | 잘못된 토큰
RATE_LIMITED | 전송 제한

«Enum» AuditActionType (Notification 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
NOTIFICATION_SYSTEM_SEND | 시스템 알림 발송
NOTIFICATION_EVENT_SEND | 이벤트 알림 발송

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-26: 알림 발송 (이벤트 기반)

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● Admin / Notification 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Notification (알림)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
notification_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 알림 고유 식별자
account_id | bigint | FK(account), NOT NULL | 알림 수신자
type | varchar(50) | NOT NULL | NotificationType ENUM (25종)
title | varchar(100) | NOT NULL | 알림 제목(푸시 표시용)
content | varchar(500) | NOT NULL | 알림 본문
ref_type | varchar(30) | nullable | NotificationRefType ENUM
ref_id | bigint | nullable | Polymorphic 참조ID (FK 아님)
link_url | varchar(500) | nullable | 클라이언트 딥링크URL
is_read | boolean | NOT NULL, DEFAULT false | 읽음 여부
read_at | datetime | nullable | 읽은 시각
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### NotificationSettings (알림 설정)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
notification_settings_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 알림 설정 고유 식별자
account_id | bigint | FK(account), UNIQUE, NOT NULL | 사용자
order_enabled | boolean | NOT NULL, DEFAULT true | 주문/결제 알림(필수)
reservation_enabled | boolean | NOT NULL, DEFAULT true | 예약 알림(필수)
chat_enabled | boolean | NOT NULL, DEFAULT true | 채팅 알림
community_enabled | boolean | NOT NULL, DEFAULT true | 커뮤니티 알림(댓글/좋아요)
store_review_enabled | boolean | NOT NULL, DEFAULT true | 상점/리뷰 알림
used_product_enabled | boolean | NOT NULL, DEFAULT true | 중고거래 알림
system_enabled | boolean | NOT NULL, DEFAULT true | 시스템 공지(필수)
marketing_enabled | boolean | NOT NULL, DEFAULT false | 마케팅/이벤트 알림(명시적 동의)
marketing_agreed_at | datetime | nullable | 마케팅 수신 동의 시각(법적 증빙)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

## 중복 참조 (common.md에도 기재됨)

- 캐싱: 사용자 unread 알림 키(`unread:account:{accountId}`, 영구, 알림 생성/읽음 시 INCR/DECR)는 `common.md` 6.4.1 참조
- 이벤트 기반 아키텍처(@TransactionalEventListener AFTER_COMMIT + @Async)는 `common.md` 6.2 참조
- FCM 푸시 Adapter 패턴(PushAdapter 인터페이스 + @Async)은 `common.md` 6.9.3 참조
- `NotificationCleanupScheduler`(매일 03:00 6개월 이전 알림 삭제 / 5분 주기 Redis unread 카운트 정합성 보정)는 CLAUDE.md 스케줄러 목록 참조
- FCM 직접 호출 금지 — 항상 도메인 이벤트 경유 원칙은 CLAUDE.md 절대 규칙 참조
