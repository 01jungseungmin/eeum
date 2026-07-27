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
