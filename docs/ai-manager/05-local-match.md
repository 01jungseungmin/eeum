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
