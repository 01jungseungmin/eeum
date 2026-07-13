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
