# Inquiry 도메인 SDD

> 코드 경로: inquiry, inquiryReply, inquiryImage
> 비고: 사용자/사장/관리자 문의

## 구현 상태

현재 Inquiry 도메인은 아직 실제 코드로 구현되지 않았다.

구현 예정 범위:

- Inquiry Entity
- InquiryReply Entity
- InquiryImage Entity
- InquiryController
- OwnerInquiryController
- AdminInquiryController
- InquiryService
- OwnerInquiryService
- AdminInquiryService
- 문의 상태 전이
- 문의 답변 처리

따라서 현재 코드에 Inquiry 관련 구현이 없는 것은 결함이 아니라 "구현 예정"으로 분류한다.

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-10. 문의

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자가 서비스 이용 중 발생하는 문의 사항을 등록하고, 관리자 또는 운영자가 이에 대해 답변할 수 있도록 하는 고객 문의 관리 기능으로, 문의 등록, 조회 및 답변 기능을 포함한다.

사용자는 서비스 이용 중 발생한 문제나 궁금한 사항에 대해 문의를 작성할 수 있으며, 문의 정보는 Inquiry 엔티티를 통해 관리된다. 각 문의는 작성자(Account)와 연관되며, 제목과 내용 등의 정보를 포함하여 관리된다.

등록된 문의는 관리자 또는 운영자가 확인할 수 있으며, 이에 대한 답변은 InquiryReply 엔티티를 통해 관리된다. 하나의 문의에 대해 여러 개의 답변이 가능하도록 설계하여 추가 설명이나 보완 답변을 유연하게 제공할 수 있도록 하였다.

각 답변은 특정 문의와 연관되며, 작성자(Account) 정보를 포함하여 누가 답변을 작성했는지 확인할 수 있도록 설계하였다. 이를 통해 사용자와 관리자 간의 원활한 소통이 가능하도록 하였다.

또한 문의 및 답변 상태를 통해 처리 진행 상황을 관리할 수 있으며, 이를 통해 사용자에게 문의 처리 여부를 명확하게 전달할 수 있도록 설계하였다.

이와 같은 구조를 통해 사용자 문의를 체계적으로 관리하고, 서비스 품질 향상 및 사용자 만족도를 높일 수 있도록 설계하였다.

> 참고: 본문에는 "하나의 문의에 대해 여러 개의 답변이 가능하도록 설계"라고 기술되어 있으나, 5.7절 InquiryReply Entity 명세에서는 `inquiryId`가 FK, UNIQUE, NOT NULL로 1:1 관계로 정의되어 있다. 원문 그대로 보존하며 불일치 가능성을 기록한다.

## 5.7 공통 도메인 내 Inquiry 명세 (원문 위치)

### ● Entity

«Entity» Inquiry
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
inquiryId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 문의 작성자
inquiryType | InquiryType | NOT NULL | STORE / SYSTEM (대상 구분)
targetId | Long | nullable | 대상 ID (STORE면 storeId, SYSTEM이면 null)
category | InquiryCategory | NOT NULL | 문의 카테고리 ENUM
title | String | NOT NULL | 문의 제목
content | String | NOT NULL, TEXT | 문의 본문
status | InquiryStatus | NOT NULL, DEFAULT PENDING | PENDING / ANSWERED / CLOSED
answeredAt | LocalDateTime | nullable | 답변 시각 (status=ANSWERED 전이 시 기록)
closedAt | LocalDateTime | nullable | 종료 시각 (status=CLOSED 전이 시 기록)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» InquiryReply
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
inquiryreplyId | Long | PK, NOT NULL | 고유 식별자
inquiryId | Long | FK, NOT NULL, UNIQUE | 문의 (1:1 보장)
accountId | Long | FK, NOT NULL | 답변자 (사장 또는 관리자)
content | String | NOT NULL, TEXT | 답변 본문
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» InquiryImage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
inquiryimageId | Long | PK, NOT NULL | 이미지 고유 식별자(ImageBase 상속)
inquiryId | Long | FK,, NOT NULL | 연관 문의
imageUrl | String | NOT NULL | 이미지 URL(ImageBase 상속)
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
createdAt | LocalDateTime | NOT NULL | ImageBase 상속
modifiedAt | LocalDateTime | NOT NULL | ImageBase 상속

«Entity» Inquiry (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
markAsAnswered() | void | PENDING → ANSWERED 전이 (answeredAt 기록)
markAsClosed() | void | ANSWERED → CLOSED 전이 (closedAt 기록)
isOwnedBy(accountId) | boolean | 본인 문의 여부
isAnswerable() | boolean | 답변 가능 상태 (PENDING)
isClosable() | boolean | 종료 가능 상태 (ANSWERED)
isStoreInquiry() | boolean | STORE 타입 여부
isSystemInquiry() | boolean | SYSTEM 타입 여부

«Entity» InquiryReply (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
isOwnedBy(accountId) | boolean | 답변자 본인 여부
updateContent(String) | void | 답변 본문 수정

### ● Controller

«Controller» InquiryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /inquiries | ResponseEntity<InquiryResponseDto> | 문의 등록 (inquiryType + targetId 지정)
GET | /inquiries/me | ResponseEntity<Page<InquiryResponseDto>> | 내 문의 목록 (status 필터)
GET | /inquiries/{inquiryId} | ResponseEntity<InquiryDetailResponseDto> | 문의 상세 (답변 + 이미지 포함)
PATCH | /inquiries/{inquiryId} | ResponseEntity<InquiryResponseDto> | 문의 수정 (PENDING 상태일 때만)
DELETE | /inquiries/{inquiryId} | ResponseEntity<CommonResponseDto> | 문의 삭제 (PENDING 상태일 때만)
PATCH | /inquiries/{inquiryId}/close | ResponseEntity<CommonResponseDto> | 문의 종료 (ANSWERED → CLOSED, 본인만)
POST | /inquiries/{inquiryId}/images | ResponseEntity<InquiryImageResponseDto> | 이미지 추가
DELETE | /inquiries/{inquiryId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 이미지 삭제

«Controller» OwnerInquiryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /owner/stores/{storeId}/inquiries | ResponseEntity<Page<InquiryResponseDto>> | 본인 매장 문의 목록 (status 필터)
GET | /owner/inquiries/{inquiryId} | ResponseEntity<InquiryDetailResponseDto> | 매장 문의 상세
POST | /owner/inquiries/{inquiryId}/reply | ResponseEntity<InquiryReplyResponseDto> | 답변 작성 (PENDING → ANSWERED)
PATCH | /owner/inquiries/{inquiryId}/reply | ResponseEntity<InquiryReplyResponseDto> | 답변 수정
DELETE | /owner/inquiries/{inquiryId}/reply | ResponseEntity<CommonResponseDto> | 답변 삭제

«Controller» AdminInquiryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/inquiries | ResponseEntity<Page<InquiryResponseDto>> | 전체 문의 조회 (type/status/category 필터)
GET | /admin/inquiries/system | ResponseEntity<Page<InquiryResponseDto>> | SYSTEM 문의 목록 (관리자 답변 대상)
GET | /admin/inquiries/{inquiryId} | ResponseEntity<InquiryDetailResponseDto> | 문의 상세
POST | /admin/inquiries/{inquiryId}/reply | ResponseEntity<InquiryReplyResponseDto> | SYSTEM 문의 답변 작성
PATCH | /admin/inquiries/{inquiryId}/reply | ResponseEntity<InquiryReplyResponseDto> | 답변 수정
DELETE | /admin/inquiries/{inquiryId}/reply | ResponseEntity<CommonResponseDto> | 답변 삭제
DELETE | /admin/inquiries/{inquiryId} | ResponseEntity<CommonResponseDto> | 문의 강제 삭제
PATCH | /admin/inquiries/{inquiryId}/close | ResponseEntity<CommonResponseDto> | 문의 강제 종료

### ● Service

«Service» InquiryService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
registerInquiry(accountId, InquiryCreateRequestDto) | InquiryResponseDto | REQUIRED | TypeValidation | 문의 등록 (inquiryType별 허용 category 검증, targetId 존재 검증)
getMyInquiries(accountId, InquirySearchDto, Pageable) | Page<InquiryResponseDto> | readOnly | - | 내 문의 목록 (status 필터)
getInquiryDetail(accountId, inquiryId) | InquiryDetailResponseDto | readOnly | Ownership | 본인 문의 상세 (답변 + 이미지 포함)
updateInquiry(accountId, inquiryId, InquiryUpdateRequestDto) | InquiryResponseDto | REQUIRED | Ownership + StatusGuard(PENDING만 허용) | 문의 수정 (PENDING 상태만)
deleteInquiry(accountId, inquiryId) | void | REQUIRED | Ownership + StatusGuard(PENDING만 허용) | 문의 삭제 (PENDING 상태만, Hard Delete + CASCADE)
closeInquiry(accountId, inquiryId) | void | REQUIRED | Ownership + StatusGuard(ANSWERED만 허용) | ANSWERED → CLOSED 전이
addInquiryImage(accountId, inquiryId, imageUrl) | InquiryImageResponseDto | REQUIRED | Ownership + ImageService | 이미지 추가
deleteInquiryImage(accountId, inquiryId, imageId) | void | REQUIRED | Ownership + ImageService | 이미지 삭제

«Service» OwnerInquiryService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getStoreInquiries(accountId, storeId, InquirySearchDto, Pageable) | Page<InquiryResponseDto> | readOnly | StoreOwnership | 본인 매장 문의 목록
getStoreInquiryDetail(accountId, inquiryId) | InquiryDetailResponseDto | readOnly | StoreOwnership | 매장 문의 상세
answerInquiry(accountId, inquiryId, InquiryReplyCreateRequestDto) | InquiryReplyResponseDto | REQUIRED | StoreOwnership + StatusGuard + Event | 답변 작성 (PENDING → ANSWERED, 알림 발송)
updateAnswer(accountId, inquiryId, InquiryReplyUpdateRequestDto) | InquiryReplyResponseDto | REQUIRED | ReplyOwnership | 답변 수정

«Service» AdminInquiryService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAllInquiries(InquirySearchDto, Pageable) | Page<InquiryResponseDto> | readOnly | Admin | 전체 문의 조회
getSystemInquiries(InquirySearchDto, Pageable) | Page<InquiryResponseDto> | readOnly | Admin | SYSTEM 문의 (관리자 답변 대상)
answerSystemInquiry(adminId, inquiryId, InquiryReplyCreateRequestDto) | InquiryReplyResponseDto | REQUIRED | Admin + StatusGuard + Event | SYSTEM 답변 작성
updateAnswer(adminId, inquiryId, InquiryReplyUpdateRequestDto) | InquiryReplyResponseDto | REQUIRED | Admin | 답변 수정
forceDeleteInquiry(inquiryId) | void | REQUIRED | Admin | 문의 강제 삭제
forceCloseInquiry(inquiryId) | void | REQUIRED | Admin | 문의 강제 종료
autoCloseExpiredInquiries() | void | - | @Scheduled(cron = "0 0 1 \* \* \*") | ANSWERED 후 7일 경과 문의 자동 CLOSED 처리 (배치)

«Helper» InquiryAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyInquiryOwnership(accountId, inquiryId) | Inquiry | 본인 문의 검증
verifyStoreInquiryAccess(accountId, inquiryId) | Inquiry | 사장이 본인 매장 문의 접근 검증 (Inquiry.targetId == owner.storeId)
verifyReplyOwnership(accountId, inquiryReplyId) | InquiryReply | 답변자 본인 검증
validateCategoryByType(InquiryType, InquiryCategory) | void | inquiryType별 허용 category 검증
validateTarget(InquiryType, Long targetId) | void | targetId 존재 검증 (STORE면 StoreRepository.existsById)
guardEditableStatus(Inquiry, allowed: List<InquiryStatus>) | void | 상태 전이 검증 (예: PENDING만 수정 가능)

### ● Component

«Component» InquiryAutoCloseScheduler
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
autoCloseExpired() | void | - | @Scheduled(cron = "0 0 1 \* \* \*") | 매일 새벽 1시 — AdminInquiryService.autoCloseExpiredInquiries() 호출 (ANSWERED 상태 + 7일 경과 → CLOSED 자동 전이)

### ● Repository

«Repository» InquiryRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long inquiryId) | Optional<Inquiry> | 문의 조회
findByIdAndAccountId(Long inquiryId, Long accountId) | Optional<Inquiry> | 본인 문의 검증용
findAllByAccountId(Long accountId, Pageable pageable) | Page<Inquiry> | 내 문의 목록
findAllByAccountIdAndStatus(Long accountId, InquiryStatus status, Pageable pageable) | Page<Inquiry> | 상태별 내 문의
findAllByInquiryTypeAndTargetId(InquiryType type, Long targetId, Pageable pageable) | Page<Inquiry> | 매장별 문의 (STORE)
findAllByInquiryTypeAndTargetIdAndStatus(InquiryType type, Long targetId, InquiryStatus status, Pageable pageable) | Page<Inquiry> | 매장별 + 상태별 문의
findAllByInquiryType(InquiryType type, Pageable pageable) | Page<Inquiry> | 시스템 문의 목록 (관리자용)
countByInquiryTypeAndTargetIdAndStatus(InquiryType type, Long targetId, InquiryStatus status) | Long | 매장 미답변 문의 수 (사장 대시보드)

«Repository» InquiryRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchInquiries(InquirySearchDto, Pageable) | Page<Inquiry> | 복합 검색 (type/category/status/기간/키워드)
findExpiredAnswered(LocalDateTime threshold) | List<Inquiry> | 자동 종료 배치용 — ANSWERED 후 N일 경과 문의

«Repository» InquiryReplyRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long inquiryreplyId) | Optional<InquiryReply> | 답변 조회
findByInquiryId(Long inquiryId) | Optional<InquiryReply> | 문의의 답변 조회(1:1)
existsByInquiryId(Long inquiryId) | boolean | 답변 존재 여부(중복 답변 방지)
deleteByInquiryId(Long inquiryId) | void | 문의 삭제 시CASCADE

## 5.8 ENUM (Inquiry 관련)

«Enum» InquiryType
ENUM 값 | 설명
---|---
STORE | 매장 문의
SYSTEM | 시스템 문의

«Enum» InquiryStatus
ENUM 값 | 설명
---|---
PENDING | 답변 대기
ANSWERED | 답변 완료
CLOSED | 문의 종료

«Enum» InquiryCategory
ENUM 값 | 설명
---|---
PRODUCT_QUESTION | 상품 문의
ORDER_ISSUE | 주문 문제
DELIVERY_ISSUE | 배송 문제
REFUND_REQUEST | 환불 요청
ACCOUNT_ISSUE | 계정 문제
PAYMENT_ISSUE | 결제 문제
BUG_REPORT | 버그 신고
FEATURE_REQUEST | 기능 요청
ETC | 기타

«Enum» AuditActionType (Inquiry 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
INQUIRY_FORCE_DELETE | 문의 삭제
INQUIRY_FORCE_CLOSE | 문의 강제 종료

«Enum» NotificationRefType (Inquiry 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
INQUIRY | 문의

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-28: 문의 등록 + 답변

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Inquiry (문의)

| 컬럼명       | 타입         | 설명                         | 설명                                     |
| ------------ | ------------ | ---------------------------- | ---------------------------------------- |
| inquiry_id   | bigint       | PK, NOT NULL, AUTO_INCREMENT | 문의 고유 식별자                         |
| account_id   | bigint       | FK(account), NOT NULL        | 문의 작성자                              |
| inquiry_type | varchar(20)  | NOT NULL                     | InquiryType ENUM                         |
| target_id    | bigint       | nullable                     | 대상ID (STORE면store_id, SYSTEM이면null) |
| category     | varchar(30)  | NOT NULL                     | InquiryCategory ENUM                     |
| title        | varchar(100) | NOT NULL                     | 문의 제목                                |
| content      | text         | NOT NULL                     | 문의 본문                                |
| status       | varchar(20)  | NOT NULL, DEFAULT 'PENDING'  | InquiryStatus ENUM                       |
| answered_at  | datetime     | nullable                     | 답변 시각(ANSWERED 전이 시)              |
| closed_at    | datetime     | nullable                     | 종료 시각(CLOSED 전이 시)                |
| created_at   | datetime     | NOT NULL                     | BaseEntity 상속                          |
| modified_at  | datetime     | NOT NULL                     | BaseEntity 상속                          |

### InquiryReply (문의 답변)

| 컬럼명           | 타입     | 설명                          | 설명                     |
| ---------------- | -------- | ----------------------------- | ------------------------ |
| inquiry_reply_id | bigint   | PK, NOT NULL, AUTO_INCREMENT  | 문의 답변 고유 식별자    |
| inquiry_id       | bigint   | FK(inquiry), UNIQUE, NOT NULL | 문의(1:1)                |
| account_id       | bigint   | FK(account), NOT NULL         | 답변자(사장 또는 관리자) |
| content          | text     | NOT NULL                      | 답변 본문                |
| created_at       | datetime | NOT NULL                      | BaseEntity 상속          |
| modified_at      | datetime | NOT NULL                      | BaseEntity 상속          |

### InquiryImage (문의 이미지)

| 컬럼명           | 타입         | 설명                         | 설명                               |
| ---------------- | ------------ | ---------------------------- | ---------------------------------- |
| inquiry_image_id | bigint       | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속) |
| inquiry_id       | bigint       | FK(inquiry), NOT NULL        | 연관 문의                          |
| image_url        | varchar(500) | NOT NULL                     | S3 이미지URL                       |
| display_order    | int          | NOT NULL, DEFAULT 0          | 표시 순서                          |
| is_thumbnail     | boolean      | NOT NULL, DEFAULT false      | 대표 이미지 여부                   |
| created_at       | datetime     | NOT NULL                     | BaseEntity 상속                    |
| modified_at      | datetime     | NOT NULL                     | BaseEntity 상속                    |

## 중복 참조 (common.md에도 기재됨)

- 문의 자동 종료 스케줄러(`InquiryAutoCloseScheduler`, 매일 01:00, ANSWERED 7일 경과 → CLOSED)는 `common.md`에도 참조 가능
- 트랜잭션 전파/AOP 정책(StatusGuard, Ownership 등)은 `common.md` 6.7, 6.8 참조
- Hard Delete + CASCADE(문의 삭제 시 답변/이미지 삭제)는 `common.md` 6.6.6 참조
