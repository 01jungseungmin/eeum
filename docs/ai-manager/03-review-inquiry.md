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
