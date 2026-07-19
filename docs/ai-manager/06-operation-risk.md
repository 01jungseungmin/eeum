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
