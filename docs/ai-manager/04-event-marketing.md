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
