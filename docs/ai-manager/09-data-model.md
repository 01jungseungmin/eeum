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
