# 이음(Eeum) 사장 웹 - AI 매니저 1차 MVP 개발 요청

## 프로젝트 배경

이 프로젝트는 사용자와 지역 상점을 연결하는 동네 기반 통합 커머스 플랫폼 **이음(Eeum)** 이다.

사용자는 활동 지역을 기반으로 상점 상품 구매, 매장 방문 예약, 중고 거래, 커뮤니티 활동, 채팅 기능을 이용하며, 사장 회원은 상품 관리, 주문 처리, 이벤트 상품 등록, 공지 관리 기능을 수행한다.

본 프로젝트는 단순 기능 구현을 넘어 실제 서비스 수준의 백엔드 아키텍처 설계를 목표로 하며, 아래 기술/설계 전략이 이미 적용되어 있다.

- Spring Boot 기반 계층형 아키텍처
    - Controller
    - Service
    - Repository
- JWT 기반 인증 + Redis 토큰 관리
- Redis 캐싱 및 분산 락 기반 성능·동시성 제어
- JPA + QueryDSL 기반 데이터 접근 계층
- 이벤트 기반 아키텍처
    - ApplicationEventPublisher
    - @TransactionalEventListener(AFTER_COMMIT)
- 경량 CQRS
    - 읽기: `@Transactional(readOnly = true)`
    - 쓰기 트랜잭션 분리
- 멱등성 보장
    - Redis
    - DB Unique 제약
- Soft Delete / Hard Delete 정책 기반 데이터 관리
- AOP 기반 인증, 감사 로그, 분산 락, 멱등성 처리 분리
- AWS 인프라
    - EC2
    - RDS
    - S3
    - Redis
- GitHub Actions CI/CD

이번 작업도 반드시 위 기조를 유지해야 한다.

너는 이 프로젝트의 **Spring Boot 백엔드 + 프론트 연동 개발자**다.  
현재 프로젝트 구조와 기존 컨벤션을 먼저 분석한 뒤, 사장 웹의 **AI 매니저 1차 MVP**를 구현해줘.

---

# 0. 개발 전 반드시 확인할 것

먼저 아래를 확인하고 기존 스타일을 그대로 따라라.

- Controller / Service / Repository / DTO / Entity 패키지 구조
- Owner 전용 API 인증 방식
- Store 소유자 검증 방식
    - 기존 `getOwnerStore(accountId)` 류 메서드 재사용
- 기존 ErrorCode / Exception 처리 방식
- Swagger 어노테이션 작성 방식
- 기존 도메인 구조
    - Notification
    - EventProduct
    - StoreReview
    - Inquiry
    - Order
    - Product
- 읽기/쓰기 트랜잭션 분리 패턴
- AOP 적용 패턴
    - 감사 로그
    - 분산 락
    - 멱등성
- 기존 테스트 스타일
    - JUnit5
    - Mockito
    - AssertJ
    - Given-When-Then
    - 메서드명 한글 가능

절대 임의로 프로젝트 구조를 새로 만들지 말고, 기존 컨벤션에 맞춰서 구현한다.

---

# 1. 목표

사장 회원이 웹에서 사용할 수 있는 **AI 매니저 기능**을 1차 MVP로 구현한다.

## 핵심 범위

- AI 매니저 메인 대시보드 조회
- AI 고객 케어 카드 조회
- AI 고객 케어 메시지 초안 생성 / 수정 / 검토 후 보내기
- 리뷰/문의 자동 대응 요약 조회
- 리뷰/문의 답변 초안 생성
- 마케팅 자동화 문구 생성
- 공지 등록용 초안 생성 / 등록 / 예약
- 생활권 매칭 분석 조회
- 생활권 매칭 노출 시작 / 상태 조회 / 중지
- 이벤트 성과 조회
- 다음 이벤트 추천
    - 구조화된 프리필 데이터 포함
- 운영 위험 조기정보 조회
- 사장님 실측값 입력
- 절감 계획 생성 / 저장
- 전력 사용 리포트 조회
- AI 활동 요약 조회
- AI 매니저 상담 챗봇
    - 고정 질문
    - 제한된 자유 입력
- 플랜 관리 조회
- 플랜별 기능 게이팅

## 1차에서 제외할 것

1차에서는 아래 기능을 실제로 연동하지 않는다.

- 실제 OpenAI API 연동
- 실제 카카오 알림톡 발송
- 실제 FCM 발송
- 실제 공공데이터 API 호출
- 실제 결제/구독 변경
- 실제 광고 노출 집행

---

# 2. 1차 MVP 개발 원칙

## 2-1. 실제 AI 연동은 하지 않는다

실제 AI 모델 호출 대신 **Template 기반 AI 문구 생성기**를 만든다.

나중에 OpenAI 또는 다른 LLM으로 교체할 수 있도록 인터페이스를 분리한다.

### 인터페이스 예시

```java
public interface AiTextGenerator {
    ...
}
```

### 구현체 예시

```java
@Component
public class TemplateAiTextGenerator implements AiTextGenerator {
    ...
}
```

챗봇 답변도 동일 인터페이스 뒤에서 템플릿 기반으로 처리한다.

---

## 2-2. 실제 외부 발송은 하지 않는다

`검토 후 보내기`, `공지 등록하기`, `예약 발송`은 실제 외부 채널 발송 대신 DB에 상태를 저장한다.

발송 상태는 아래 Enum으로 관리한다.

- DRAFT
- REVIEWED
- SENT
- SCHEDULED
- FAILED
- CANCELLED

기존 Notification 도메인과 쉽게 연결할 수 있게 설계하되, 1차에서는 실제 푸시/알림톡 전송까지 연결하지 않는다.

발송 이벤트가 필요하면 기존 이벤트 기반 아키텍처 패턴으로 발행만 해두고, 리스너는 로그 기록 수준으로 둔다.

- ApplicationEventPublisher 사용
- `@TransactionalEventListener(AFTER_COMMIT)` 사용

---

## 2-3. 실제 데이터 우선, 부족하면 빈 상태 반환

기존 주문, 상품, 이벤트, 리뷰, 문의 데이터가 있으면 그것을 기반으로 집계한다.

데이터가 부족하면 하드코딩된 숫자 대신 아래 형태로 응답한다.

- 빈 리스트
- 0
- null
- `hasData = false`
- `emptyMessage`

단, AI 문구 생성 문장은 템플릿 기반 생성을 허용한다.

---

## 2-4. 동시성 / 멱등성

아래 기능은 중복 클릭 시 1회만 처리되도록 기존 멱등성 패턴을 재사용한다.

- 초안 발송 처리
- 공지 등록
- 예약 발송
- 생활권 노출 시작

사용 가능한 방식:

- Redis
- DB Unique 제약
- 기존 Idempotency AOP
- 기존 분산 락 패턴

사용량 카운트는 동시 요청 시 월 제한을 초과하지 않도록 처리한다.

- 기존 분산 락 사용
- 또는 DB 제약 활용

## 2-5. 추후 실제 AI 연동 대상

1차 MVP에서는 Template 기반으로 처리하지만, 추후 실제 LLM 또는 AI API를 연동할 대상은 아래 기능이다.

- AI 고객 케어 메시지 초안 생성
- 리뷰 답글 초안 생성
- 문의 답변 초안 생성
- 반복 불만 키워드 대응 문구 생성
- 마케팅 자동화 문구 생성
- 공지 등록용 문구 생성
- 이벤트 성과 자연어 요약
- 다음 이벤트 추천 문구 및 추천 이유 생성
- 생활권 매칭 추천 이유 생성
- 운영 위험 조기정보의 AI 판단 문구 생성
- 안전/에너지 체크리스트 추천
- 절감 계획 생성
- 전력 사용 리포트 핵심 진단 생성
- AI 매니저 상담 챗봇 답변 생성

단, 아래 기능은 AI 연동 대상이 아니다.

- 대시보드 숫자 집계
- 고객 수 계산
- 최근 7일 알림 제외 고객 계산
- 발송 상태 변경
- 예약 시간 검증
- 플랜 제한
- 사용량 카운트
- owner/store 권한 검증
- 공공데이터 API 호출
- 알림톡/FCM 실제 발송
- 결제/구독 처리

따라서 실제 AI 연동은 AiTextGenerator 또는 AiInsightGenerator 인터페이스 뒤에서만 수행한다. 
AiTextGenerator는 고객·마케팅 메시지 등 발송용 문구 생성을, AiInsightGenerator는 리스크 판단·매칭 이유·요약 등 분석/판단성 텍스트 생성을 담당한다. 
1차에서는 둘 다 템플릿 구현체(TemplateAiTextGenerator, TemplateAiInsightGenerator)로 시작한다. Controller나 Service에서 OpenAI API를 직접 호출하지 않는다.

---

# 3. 구현 범위

## 3-1. AI 매니저 메인 대시보드

### API

```http
GET /api/owner/ai-manager/dashboard
```

### 응답 포함 내용

- 보고 기준 시각
    - 예: 오늘 오전 8:00 기준
- 오늘 처리할 항목 수
- 개인정보 안내 문구
    - 예: 고객 동의 범위 내에서 제공되는 관계 신호 기반입니다.
- AI 고객 케어 카드 3개 요약
    - 구매 관심이 높은 고객
    - 한동안 방문이 없는 단골
    - 문의 후 망설이는 고객
- 리뷰/문의 자동 대응 요약
    - 미답변 리뷰 수
    - 미답변 문의 수
    - 반복 불만 키워드 수
- 이벤트 성과 요약
    - 상품 조회수
    - 주문 전환율
    - 신규 고객 비중
    - 단골 재주문 수
- 생활권 매칭 점수
- 운영 위험 조기정보 요약
- AI 활동 요약

### DTO 예시

- AiManagerDashboardResponseDto
- AiCustomerCareSummaryDto
- AiReviewInquirySummaryDto
- AiEventPerformanceSummaryDto
- AiLocalMatchSummaryDto
- AiOperationRiskSummaryDto
- AiActivitySummaryDto

---

## 3-2. AI 고객 케어

### API

```http
GET /api/owner/ai-manager/customer-care
GET /api/owner/ai-manager/customer-care/{careType}
POST /api/owner/ai-manager/customer-care/{careType}/draft
```

### careType

- CART_INTEREST
- INACTIVE_REGULAR
- INQUIRY_HESITATION

### 응답 포함 내용

- 제목
- 우선순위
- 대상 고객 수
- 판단 이유
- AI 준비 메시지
- 최근 7일 알림 제외 고객 수
- 발송 가능 여부

### 초안 예시

#### CART_INTEREST

```text
담아두신 김치찌개 세트가 오늘 점심 포장 할인 중입니다. 필요하실 때 편하게 이용해보세요.
```

#### INACTIVE_REGULAR

```text
오랜만이에요. 자주 찾아주셨던 메뉴가 이번 주 다시 준비되었습니다.
```

#### INQUIRY_HESITATION

```text
남겨주신 문의 관련해 안내드려요. 궁금하신 점 있으시면 편하게 말씀해주세요.
```

---

## 3-3. AI 생성 메시지 공통 관리

AI가 만든 모든 문구는 공통 테이블인 `AiGeneratedMessage`에 저장한다.

### API

```http
GET /api/owner/ai-manager/generated-messages
GET /api/owner/ai-manager/generated-messages/{messageId}
PATCH /api/owner/ai-manager/generated-messages/{messageId}
POST /api/owner/ai-manager/generated-messages/{messageId}/send
POST /api/owner/ai-manager/generated-messages/{messageId}/schedule
POST /api/owner/ai-manager/generated-messages/{messageId}/cancel
```

### Entity 필드 예시

#### AiGeneratedMessage

- id
- store
- ownerAccount
- type
- targetType
- targetId
- title
- content
- originalContent
- status
- channel
- scheduledAt
- sentAt
- createdAt
- updatedAt

### type

- CUSTOMER_CARE
- REVIEW_REPLY
- INQUIRY_REPLY
- COMPLAINT_REPLY
- NOTICE
- EVENT_MARKETING
- RISK_GUIDE
- SAVING_PLAN

### channel

- APP_PUSH
- KAKAO_ALERT
- STORE_NOTICE
- SNS_CARD

---

## 3-4. 리뷰/문의 자동 대응

### API

```http
GET /api/owner/ai-manager/review-inquiries
POST /api/owner/ai-manager/reviews/{reviewId}/reply-draft
POST /api/owner/ai-manager/inquiries/{inquiryId}/reply-draft
POST /api/owner/ai-manager/review-inquiries/complaint-draft
POST /api/owner/ai-manager/review-inquiries/notice-draft
```

### 응답 포함 내용

- 최근 2주 반복 불만 키워드
- 미답변 리뷰 수
- 미답변 문의 수
- 미답변 리뷰 목록
- 미답변 문의 목록
- AI 추천 대응 문구

### 주의사항

기존 리뷰 답글 등록 API가 있으면 1차에서는 draft만 생성하고 기존 API와 연결 가능하게 한다.

기존 답글 등록 API가 명확하면 `send` 시 실제 답글 등록까지 연결해도 된다.

---

## 3-5. 이벤트 성과 매니저

### API

```http
GET /api/owner/ai-manager/events/performance
```

### 응답 포함 내용

- 상품 조회수 변화
- 주문 전환율 변화
- 신규 고객 비중 변화
- 단골 재주문 수
- AI 요약
- 다음 이벤트 추천

### 다음 이벤트 추천 응답 구조

텍스트 메시지만 반환하지 말고, 프론트 이벤트 등록 화면의 자동 입력을 위해 구조화된 프리필 데이터를 포함한다.

필드 예시:

- recommendedProductId
- discountType
    - PERCENT
    - AMOUNT
    - SERVICE
- discountRate
- discountAmount
- recommendedTimeRange
    - 예: 11:00~14:00
- matchBasedExposure
    - 매칭 점수 기준 노출 추천 여부
- reason

### 집계 기준

- 이벤트 기간 중 주문 수
- 이벤트 상품 조회수 필드가 있으면 사용
- 없으면 0 또는 null
- 신규 고객 비중은 해당 기간 첫 주문 고객 기준으로 계산 가능하면 계산
- 계산이 어렵다면 DTO 필드는 유지하고 null 반환

---

## 3-6. 마케팅 자동화 매니저

### API

```http
GET /api/owner/ai-manager/marketing
POST /api/owner/ai-manager/marketing/draft
```

### 요청 값

#### noticeType

- EVENT
- TEMP_CLOSED
- NEW_MENU

#### tone

- POLITE
- FRIENDLY
- SHORT

#### channels

- APP_PUSH
- KAKAO_ALERT
- STORE_NOTICE
- SNS_CARD

### 응답 포함 내용

- 생성 제목
- 생성 본문
- 예상 도달 수
- 선택 채널 수
- 글자 수
- 발송 가능 여부
- channelReaches

### channelReaches 예시

```json
[
  {
    "channel": "KAKAO_ALERT",
    "estimatedReach": 120
  },
  {
    "channel": "APP_PUSH",
    "estimatedReach": 80
  },
  {
    "channel": "STORE_NOTICE",
    "estimatedReach": 40
  }
]
```

예상 도달 수는 단일 고정값이 아니라 채널별 도달 추정치의 합산 구조로 설계한다.

실제 데이터가 없으면 채널별 0을 반환한다.

---

## 3-7. 공지 등록 화면용 API

### API

```http
POST /api/owner/ai-manager/notices/draft
POST /api/owner/ai-manager/notices/{messageId}/publish
POST /api/owner/ai-manager/notices/{messageId}/schedule
```

### 주의사항

마케팅 초안 생성은 채널 4종을 지원한다.

- APP_PUSH
- KAKAO_ALERT
- STORE_NOTICE
- SNS_CARD

하지만 공지 등록 화면에서는 아래 3종만 실제 발송 채널로 사용한다.

- KAKAO_ALERT
- APP_PUSH
- STORE_NOTICE

`SNS_CARD`는 문구 생성용으로만 쓰이며, 실제 공지 발송 채널에서는 제외한다.

이를 스펙과 검증 로직에 반영한다.

예약 발송 시간이 현재보다 과거면 예외를 발생시킨다.

```text
AI_INVALID_SCHEDULE_TIME
```

실제 Store Notice 도메인이 있으면 연동하고, 없으면 `AiGeneratedMessage` 상태만 `SENT` 또는 `SCHEDULED`로 변경한다.

---

## 3-8. 생활권 매칭 매니저

### API

```http
GET /api/owner/ai-manager/local-match
PATCH /api/owner/ai-manager/local-match/conditions
POST /api/owner/ai-manager/local-match/exposure
GET /api/owner/ai-manager/local-match/exposure
POST /api/owner/ai-manager/local-match/exposure/stop
```

### 조회 응답 포함 내용

- 종합 매칭 점수
- 지역 일치율
- 관심사 일치율
- 이벤트 적합도
- 단골 고객 비중
- 노출 대상 고객 세그먼트
    - 마포구 한식 관심 고객
    - 점심 이벤트 반응 고객
    - 우리 가게 단골
    - 반경 1.5km 신규 유입
- 매칭 이유
- 예상 노출 대상 수

### 조건 변경 요청

#### radiusKm

- 1
- 1.5
- 3

#### interest

- 예: 한식
- 예: 분식
- 예: 카페

#### customerType

- ALL
- REGULAR
- NEW

### 조건 변경 응답

변경 후 추정 노출 대상 수를 포함한다.

### 노출 상태 조회 응답

- 진행 여부
- 시작 시각
- 노출 중 대상 수
- 반경
- 관심사
- 고객 유형

### 정책

1차에서는 실제 광고 노출을 하지 않는다.

대신 아래 방식으로 처리한다.

- 노출 시작/중지를 `AiActionLog`에 기록
- 노출 상태는 별도 상태 저장 엔티티 또는 최신 로그 기준으로 관리
- 이미 진행 중인 상태에서 다시 노출 시작 시 예외 발생

```text
AI_INVALID_STATUS
```

---

## 3-9. 운영 위험 조기정보

### API

```http
GET /api/owner/ai-manager/operation-risks
GET /api/owner/ai-manager/operation-risks/detail
POST /api/owner/ai-manager/operation-risks/owner-input
```

### 조회 응답 포함 내용

- 종합 위험 신호
    - WARNING
    - CAUTION
    - NORMAL
- 동네 에너지 경기 신호
- 계절/시기 선제 알림
- 업종 활동 이상 변화 감지
- 안전 리스크 체크
- AI 판단
- 준비된 대응 체크리스트
- 데이터 출처 투명성
- 저장된 절감 계획 존재 여부
    - hasSavedPlan

### 데이터 출처 타입

1차에서는 실제 공공데이터 API를 호출하지 않는다.  
대신 출처 타입만 구조화한다.

- PRECISE_MEASURED
- OWNER_INPUT
- LOCAL_AVERAGE_ONLY

DTO에는 아래 필드를 포함한다.

- sourceType
- sourceLabel
- description

### 사장님 실측값 입력 API

`OWNER_INPUT` 소스 타입이 실제로 동작하려면 사장님이 월 전력 사용량, 주요 설비 정보 등을 직접 입력할 수 있어야 한다.

간단한 엔티티로 저장한다.

#### Entity 예시

##### AiOwnerMetricInput

- id
- store
- metricType
    - 예: MONTHLY_POWER_KWH
- value
- yearMonth
- createdAt
- updatedAt

조회 시 입력값이 있으면 `sourceType = OWNER_INPUT`으로 반영한다.

입력값이 없으면 `LOCAL_AVERAGE_ONLY`로 폴백한다.

---

## 3-10. 절감 계획

### API

```http
POST /api/owner/ai-manager/operation-risks/saving-plan
POST /api/owner/ai-manager/operation-risks/saving-plan/save
```

### 응답 포함 내용

- 절감 항목 목록
    - 냉방 시간대 관리
    - 냉장 설비 점검
    - 고효율 설비 교체 검토
- 난이도
- 시작 시점
- 월 예상 절감액
- 선택 여부
- 총 예상 절감액
- 실행 일정

### Entity 설계

필요하면 아래처럼 분리한다.

#### AiSavingPlan

- id
- store
- title
- expectedMonthlySavingAmount
- status
- createdAt
- updatedAt

#### AiSavingPlanItem

- id
- savingPlan
- title
- difficulty
- startTiming
- expectedMonthlySavingAmount
- selected

1차에서 복잡하면 `AiGeneratedMessage`의 `type = SAVING_PLAN`으로 저장해도 된다.

저장 완료 후 `3-9 운영 위험 조기정보 조회`의 `hasSavedPlan`이 `true`가 되어야 한다.

---

## 3-11. 전력 사용 리포트

### API

```http
GET /api/owner/ai-manager/operation-risks/electricity-report
```

### 응답 포함 내용

- 최근 6개월 월별 사용량
- 설비별 사용 비중
    - 냉방·공조
    - 냉장·냉동
    - 조리 설비
    - 조명·기타
- 핵심 진단
- 분석 기간
- 업종 비교
- 추정 절감액

`3-9 owner-input` 입력값이 있으면 그것을 우선 사용한다.

리포트 다운로드 API는 1차에서 만들지 않는다.  
단, 추후 확장을 위해 endpoint 이름만 고려한다.

---

## 3-12. AI 활동 요약

### API

```http
GET /api/owner/ai-manager/activities/summary
```

### 응답 포함 내용

- 기간
- 리뷰 답글 초안 수
- 문의 답변 초안 수
- 단골 메시지 수
- 이탈 고객 알림 수
- AI 활동 이후 반응
    - 단골 메시지 발송 후 재방문 수
    - 이벤트 알림 후 주문 전환 수
    - 미답변 문의 유지 수

`AiActionLog`와 `AiGeneratedMessage` 기준으로 집계한다.

---

## 3-13. 플랜 관리

### API

```http
GET /api/owner/ai-manager/plans
```

### 응답 포함 내용

- 현재 플랜
- 각 플랜 정보
    - Free
    - AI Basic
    - AI Pro
- 각 플랜 가격
- 각 플랜 기능 목록
- 현재 사용량
- 월 AI 추천 제한

### 플랜 예시

#### Free

- 가격: 무료
- 기본 상품·이벤트 관리 가능
- 월 제한 AI 추천
- 홍보 문구 생성 제한
- AI 고객 케어/리스크 분석 불가

#### AI Basic

- 가격: 19,000원 / 월
- AI 추천 월 30회
- 이벤트 추천·홍보 문구 생성
- 기본 생활권 매칭 분석

#### AI Pro

- 가격: 39,000원 / 월
- AI 고객 케어 고급 분석
- 생활권 매칭 우선 노출
- 리뷰/문의 위험 신호 분석
- 에너지·안전 리스크 고급 분석
- AI 활동 요약 리포트
- 우선 고객 지원

1차에서는 실제 결제/구독 변경은 구현하지 않는다.

현재 플랜은 BASIC으로 고정하거나, DB에 store별 `planType`을 저장한다.

### 플랜별 기능 게이팅 매핑

서비스 레이어에서 검증한다.

| 기능 | Free | Basic | Pro |
|---|---:|---:|---:|
| 대시보드 조회 | O | O | O |
| AI 고객 케어 카드 조회 | O | O | O |
| AI 고객 케어 초안 생성 | X | O / 월 30회 합산 | O |
| 리뷰/문의 답변 초안 | X | O / 월 30회 합산 | O |
| 마케팅 문구 생성 | X | O / 월 30회 합산 | O |
| 공지 등록/예약 | O / 직접 작성 | O | O |
| 생활권 매칭 조회 | X | O / 기본 분석 | O |
| 생활권 매칭 노출 시작 | X | X | O / 우선 노출 |
| 운영 위험 조기정보 | X | 요약만 | O / 상세 포함 |
| 절감 계획 / 전력 리포트 | X | X | O |
| AI 활동 요약 | X | 기본 | O / 리포트 |
| AI 챗봇 | X | O / 월 30회 합산 | O |

플랜 미달 시 아래 예외를 발생시킨다.

```text
AI_PLAN_REQUIRED
```

표의 세부 배치는 기존 기획과 충돌하면 조정 가능하다.

단, **어떤 API가 어느 플랜부터 열리는지**는 코드상 한 곳에서 관리한다.

예시:

- Enum
- Policy class
- AiPlanPolicy

---

## 3-14. AI 매니저 상담 챗봇

배너의 `AI 점장에게 직접 물어보기`로 진입하는 대화 화면용 API를 구현한다.

### API

```http
GET /api/owner/ai-manager/chat/quick-questions
POST /api/owner/ai-manager/chat/messages
```

### 고정 추천 질문 8종

- 이번 주 이벤트 뭐 할까요?
- 오늘 공지 문구 써줘
- 우리 가게 리뷰 요약해줘
- 단골 고객 메시지 써줘
- 미답변 문의 답변 초안 만들어줘
- 이번 이벤트 성과 요약해줘
- 에너지·안전 점검 항목 알려줘
- 답글 초안 써줘

### 메시지 전송 요청

고정 질문 id 또는 자유 입력 텍스트를 받을 수 있어야 한다.

### 응답 구조

```json
{
  "text": "답변 본문",
  "actions": [
    {
      "label": "이벤트 등록으로 이동",
      "actionType": "OPEN_EVENT_REGISTER"
    }
  ]
}
```

### actionType 예시

- OPEN_EVENT_REGISTER
- OPEN_NOTICE_REGISTER
- OPEN_REVIEW_DRAFT
- OPEN_SAFETY_CHECK
- SEND_MESSAGE
- REGENERATE

프론트는 `actionType`을 기준으로 화면 전환 또는 기존 API 호출을 수행한다.

### 범위 밖 처리

아래 키워드가 포함되면 답변을 생성하지 않고 안내 메시지를 반환한다.

- 세금
- 부가세
- 종소세
- 주휴수당
- 4대보험
- 노무
- 근로계약
- 법률
- 소송
- 임대료
- 권리금
- 배달의민족
- 배민
- 쿠팡이츠
- 외부 플랫폼 비교

### 범위 밖 안내 메시지

```text
해당 내용은 AI 매니저의 지원 범위 밖이에요. AI 매니저는 이음에 등록된 가게 운영 데이터를 기반으로 리뷰·문의·이벤트·고객 메시지·공지 문구·에너지·안전 체크만 도와드릴 수 있어요. 세무·노무·법률·외부 플랫폼 비교는 해당 전문가나 기관에 문의해 주세요.
```

키워드 목록은 코드 상수 또는 설정으로 분리해 확장 가능하게 한다.

범위 안 자유 입력에 대한 기본 응답도 템플릿으로 처리한다.

### 챗봇 사용량 정책

챗봇 답변 중 문구 생성성 답변은 월 사용량 카운트에 포함한다.

사용량 카운트 포함 예시:

- 공지 문구 생성
- 고객 메시지 생성
- 답글 초안 생성
- 문의 답변 초안 생성

사용량 카운트 제외 예시:

- 리뷰 요약
- 성과 요약
- 위험 정보 단순 조회
- 범위 밖 안내 메시지

1차에서는 대화 이력 저장은 선택 사항이다.

저장한다면 아래 엔티티를 사용한다.

#### AiChatMessage

- id
- store
- owner
- role
    - USER
    - ASSISTANT
- content
- createdAt

---

# 4. 권한 정책

모든 API는 사장 회원만 접근 가능해야 한다.

검증 조건:

- 로그인한 accountId 확인
- 해당 account가 `ROLE_OWNER` 권한인지 확인
- 해당 owner가 소유한 store만 접근 가능
- 다른 사장의 store 또는 메시지 접근 시 예외 발생

```text
AI_FORBIDDEN
```

기존 `getOwnerStore(accountId)` 류 메서드를 재사용한다.

---

# 5. 사용량 제한

AI 문구 생성 API는 플랜별 사용량 제한을 적용할 수 있도록 구조를 만든다.

## 최소 구현

- `AiUsageLog` 또는 `AiUsageCounter` 생성
- 월 기준 사용량 카운트
- Basic: 월 30회
    - 고객 케어 초안
    - 리뷰/문의 초안
    - 마케팅 문구
    - 챗봇 생성성 답변
    - 위 항목 합산
- Pro: 제한 없음
- Free: 생성 제한
    - 조회성 기능만 허용

## 동시성 처리

동시 요청 시 초과 생성되지 않도록 처리한다.

사용 가능한 방식:

- 기존 분산 락 패턴
- DB Unique 제약
- Redis atomic increment

## 예외

월 사용량 초과 시:

```text
AI_USAGE_LIMIT_EXCEEDED
```

플랜 미달 시:

```text
AI_PLAN_REQUIRED
```

---

# 6. 추천 Entity 구조

기존 프로젝트 컨벤션에 맞춰 이름은 조정 가능하다.

컨벤션 예시:

- BaseEntity 상속
- Java 필드명: camelCase
- DB 컬럼명: snake_case

---

## 6-1. AiGeneratedMessage

AI가 생성한 모든 문구를 저장한다.

### 필드 예시

- id
- store
- ownerAccount
- type
- targetType
- targetId
- title
- content
- originalContent
- status
- channel
- scheduledAt
- sentAt
- createdAt
- updatedAt

---

## 6-2. AiActionLog

AI 매니저 내 주요 액션을 기록한다.

### 필드 예시

- id
- store
- ownerAccount
- actionType
- targetType
- targetId
- description
- createdAt
- updatedAt

---

## 6-3. AiPlanSubscription

store별 AI 플랜 정보를 저장한다.

### 필드 예시

- id
- store
- planType
- startedAt
- expiredAt
- active
- createdAt
- updatedAt

---

## 6-4. AiUsageLog

월별 AI 사용량을 기록한다.

### 필드 예시

- id
- store
- ownerAccount
- usageType
- yearMonth
- createdAt
- updatedAt

---

## 6-5. AiExposureStatus

생활권 노출 상태를 관리한다.

### 필드 예시

- id
- store
- active
- startedAt
- stoppedAt
- targetCount
- radiusKm
- interest
- customerType
- createdAt
- updatedAt

간단히 가려면 `AiActionLog` 최신 기록 기준으로 대체할 수 있다.

다만 상태 조회 API가 O(1)에 가깝게 동작하도록 별도 상태 저장을 권장한다.

---

## 6-6. AiOwnerMetricInput

사장님 실측값을 저장한다.

### 필드 예시

- id
- store
- metricType
    - 예: MONTHLY_POWER_KWH
- value
- yearMonth
- createdAt
- updatedAt

---

## 6-7. 선택 Entity

### AiSavingPlan

- id
- store
- title
- expectedMonthlySavingAmount
- status
- createdAt
- updatedAt

### AiSavingPlanItem

- id
- savingPlan
- title
- difficulty
- startTiming
- expectedMonthlySavingAmount
- selected
- createdAt
- updatedAt

### AiChatMessage

- id
- store
- ownerAccount
- role
- content
- createdAt
- updatedAt

---

# 7. Enum 예시

## AiMessageType

- CUSTOMER_CARE
- REVIEW_REPLY
- INQUIRY_REPLY
- COMPLAINT_REPLY
- NOTICE
- EVENT_MARKETING
- LOCAL_MATCH
- RISK_GUIDE
- SAVING_PLAN

## AiMessageStatus

- DRAFT
- REVIEWED
- SENT
- SCHEDULED
- FAILED
- CANCELLED

## AiCareType

- CART_INTEREST
- INACTIVE_REGULAR
- INQUIRY_HESITATION

## AiPlanType

- FREE
- BASIC
- PRO

## AiRiskLevel

- NORMAL
- CAUTION
- WARNING

## AiDataSourceType

- PRECISE_MEASURED
- OWNER_INPUT
- LOCAL_AVERAGE_ONLY

## AiChatActionType

- OPEN_EVENT_REGISTER
- OPEN_NOTICE_REGISTER
- OPEN_REVIEW_DRAFT
- OPEN_SAFETY_CHECK
- SEND_MESSAGE
- REGENERATE

## AiDiscountType

- PERCENT
- AMOUNT
- SERVICE

## AiChannel

- APP_PUSH
- KAKAO_ALERT
- STORE_NOTICE
- SNS_CARD

## AiCustomerType

- ALL
- REGULAR
- NEW

---

# 8. ErrorCode 추가

기존 ErrorCode 방식에 맞춰 추가한다.

- AI_PLAN_REQUIRED
- AI_USAGE_LIMIT_EXCEEDED
- AI_MESSAGE_NOT_FOUND
- AI_MESSAGE_ALREADY_SENT
- AI_MESSAGE_NOT_EDITABLE
- AI_FORBIDDEN
- AI_GENERATION_FAILED
- AI_INVALID_STATUS
    - 노출 중복 시작
    - 잘못된 상태 전이 등
- AI_INVALID_SCHEDULE_TIME
- AI_CHAT_OUT_OF_SCOPE
    - 선택 사항
    - 안내 메시지를 정상 응답으로 처리한다면 불필요

---

# 9. Swagger 문서화

모든 Controller와 DTO에 Swagger 설명을 추가한다.

## Swagger 태그

```text
Owner AI Manager
```

설명은 사장 웹 기준으로 작성한다.

---

# 10. 테스트

기존 테스트 스타일에 맞춰 작성한다.

- Given-When-Then
- JUnit5
- Mockito
- AssertJ
- 메서드명 한글 가능

커버리지 목표:

- Service: 70%
- Controller: 60%

---

## 10-1. Service 테스트

아래 테스트를 추가한다.

- 고객 케어 카드 조회 성공
- 고객 케어 초안 생성 성공
- 생성된 초안 수정 성공
- 이미 SENT 상태인 메시지 수정 불가
- Basic 플랜 월 30회 초과 시 예외
- Free 플랜이 생성 API 호출 시 `AI_PLAN_REQUIRED`
- 다른 사장의 메시지 접근 불가
- 예약 시간이 현재보다 과거면 예외
- 운영 위험 정보 조회 성공
- 사장님 실측값 입력 후 `sourceType = OWNER_INPUT` 반영 확인
- 절감 계획 생성 성공
- 절감 계획 저장 후 `hasSavedPlan = true` 확인
- 노출 시작 성공
- 이미 진행 중일 때 중복 시작 시 예외
- 노출 중지 성공
- 챗봇 범위 밖 키워드 입력 시 안내 메시지 반환
- 챗봇 생성성 답변은 사용량 카운트
- 챗봇 조회성 답변은 사용량 미카운트

---

## 10-2. Controller 테스트

가능하면 아래 테스트를 추가한다.

- owner 인증 필요
- owner 권한이 아니면 접근 불가
- 응답 DTO 구조 확인
- 주요 API HTTP status 확인

---

# 11. 1차 개발에서 하지 말 것

이번 작업에서는 아래를 하지 않는다.

- 실제 OpenAI API 연동
- 실제 카카오 알림톡 발송
- 실제 FCM 발송
- 실제 결제/구독 변경
- 실제 공공데이터 API 호출
- 실제 광고 노출 집행
- 복잡한 머신러닝 추천 로직
- 대규모 통계 최적화
- 화면 숫자를 하드코딩해서 무조건 340명, 92점처럼 반환하는 것

단, 화면 연동을 위해 DTO 필드와 API 구조는 완성한다.

---

# 12. 완료 기준

작업 완료 후 아래를 반드시 확인한다.

- Gradle build 성공
- 기존 테스트 실패 없음
- 새 테스트 통과
- Swagger에서 API 확인 가능
- owner 권한 검증 적용
- store 소유자 검증 적용
- AI 생성 메시지 저장 가능
- AI 생성 메시지 수정 가능
- AI 생성 메시지 발송 상태 변경 가능
- 예약 발송 상태 변경 가능
- 노출 시작 동작
- 노출 상태 조회 동작
- 노출 중지 동작
- 플랜 게이팅 구조 존재
- 사용량 제한 구조 존재
- 챗봇 고정 질문 응답 가능
- 챗봇 자유 입력 응답 가능
- 챗봇 범위 밖 처리 가능
- 실제 데이터가 없어도 API가 500 에러 없이 빈 상태로 응답
- 프론트가 바로 연결할 수 있는 응답 DTO 제공

---

# 13. 작업 결과 보고 형식

작업이 끝나면 아래 형식으로 보고해줘.

## 13-1. 추가/수정한 파일 목록

- 파일 경로
- 변경 내용 요약

## 13-2. 추가한 API 목록

- Method
- URL
- 설명

## 13-3. 추가한 Entity/Enum 목록

- Entity명
- Enum명
- 주요 필드

## 13-4. 주요 로직 설명

- 대시보드 조회 로직
- AI 문구 생성 로직
- 메시지 상태 전이 로직
- 생활권 노출 상태 관리 로직
- 운영 위험 정보 조회 로직
- 챗봇 응답 로직

## 13-5. 플랜/사용량 제한 처리 방식

- 플랜별 허용 기능
- Basic 월 30회 제한 방식
- Free 제한 방식
- Pro 무제한 처리 방식
- 동시성 제어 방식

## 13-6. 챗봇 범위 판정 및 액션 카드 처리 방식

- 고정 질문 처리 방식
- 자유 입력 처리 방식
- 범위 밖 키워드 처리 방식
- actionType 반환 방식

## 13-7. 테스트 결과

- 실행한 테스트 명령어
- 통과 여부
- 실패 시 원인

## 13-8. 2차 개발로 넘길 TODO

- 실제 LLM 연동
- 실제 알림톡/FCM 발송
- 실제 공공데이터 API 연동
- 실제 구독/결제 연동
- 실제 광고 노출 집행
- 통계 고도화
- 성능 최적화