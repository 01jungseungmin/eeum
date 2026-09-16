## 📄 작업 유형
- [ ] 기능 개발
- [x] 화면 개발
- [ ] API 개발
- [ ] 문서 수정
- [ ] 버그 수정
- [ ] 리팩토링
- [ ] 테스트

## 🎯 작업 목적
AI 매니저 1차 MVP의 핵심 기능인 **AI 고객 케어** 화면을 구현한다.

구매 관심이 높은 고객 / 한동안 방문이 없는 단골 / 문의 후 망설이는 고객 3개 카드를 보여주고, 사장님이 카드별로 메시지 초안을 생성해 검토 후 발송할 수 있게 한다.

동시에 2번 이슈에서 만든 공용 `AiMessageDraftModal` 을 **처음으로 재사용**해 설계가 실제로 성립하는지 검증하고, 이후 마케팅·리뷰/문의 이슈에서 반복될 **초안 생성 실패 처리(AI_015 등)** 를 공용 훅으로 확정한다.

## 📌 작업 내용
- `src/api/owner/aiCustomerCareApi.js` 신설 — 카드 전체/단건 조회, 케어 유형별 초안 생성
- `src/hooks/useAiDraft.js` 신설 — **초안 생성 공용 훅**
  - 409 `AI_015`(초안 보관 개수 초과) 응답 시 `currentCount / limit` 을 포함한 확인창을 띄우고, 승인 시 `confirmDelete: true` 로 재요청
  - `AI_PLAN_REQUIRED`, `AI_USAGE_LIMIT_EXCEEDED` 등 그 외 실패는 백엔드 문구를 그대로 노출
  - 생성된 메시지를 `draft` 로 보관해 `AiMessageDraftModal` 에 그대로 전달
- `src/components/owner/ai/AiCareDetailCard.jsx` 신설 — 대상 고객 수, AI 판단 이유, 준비된 문구, 최근 7일 알림 제외 인원, 채널 선택 + 힌트 입력 + 초안 생성 버튼
- `src/pages/owner/main/AiCustomerCarePage.jsx` 신설 — 카드 3종 렌더링, 대시보드에서 넘어온 `?careType=` 쿼리로 해당 카드 하이라이트, 초안 생성 → 모달 연결
- `App.jsx` 에 `/ai-manager/customer-care` 라우트, `MenuConfig.jsx` 에 `AI 고객 케어` 메뉴 추가

## ✅ 완료 조건
- [ ] `/ai-manager/customer-care` 에서 케어 카드 3종이 우선순위대로 노출된다
- [ ] 대시보드의 케어 카드 클릭 시 `?careType=` 로 이동하고 해당 카드가 강조된다
- [ ] `sendable === false` 이거나 대상 고객이 0명이면 초안 생성 UI 대신 안내 문구가 나온다
- [ ] `recentlyNotifiedExcludedCount > 0` 이면 최근 7일 알림 제외 인원이 표시된다
- [ ] 채널(앱 푸시/카카오 알림톡/상점 공지)과 힌트를 지정해 초안을 생성할 수 있다
- [ ] 초안 생성 성공 시 공용 `AiMessageDraftModal` 이 열리고 검토 후 발송/예약할 수 있다
- [ ] 초안 보관 개수 초과(409 AI_015) 시 확인창이 뜨고, 승인하면 `confirmDelete: true` 로 재요청되어 정상 생성된다
- [ ] 발송/예약 후 카드 목록이 갱신된다
- [ ] `npm run build` 성공

## 🔗 관련 문서 / API
- `docs/ai-manager/02-customer-care.md`
- Swagger: `OwnerAiCustomerCareController` — `GET /owner/ai-manager/customer-care`, `GET /{careType}`, `POST /{careType}/draft`
- DTO: `AiCustomerCareCardDto`, `AiCustomerCareDraftRequestDto`, `AiDraftCapacityExceededResponseDto`
- 선행 이슈: `01-web-ai-manager-dashboard.md`, `02-web-ai-generated-message.md`

## 📎 참고 사항
- 초안 생성은 **Basic 플랜 이상**이며 월 사용량이 카운트된다. `AI_PLAN_REQUIRED`(403) / `AI_USAGE_LIMIT_EXCEEDED`(429) 는 현재 백엔드 문구를 그대로 노출하며, 플랜 업그레이드 유도 UI는 7번(플랜/결제) 이슈에서 붙인다.
- `AI_015` 는 `BusinessException` 에 `data` 를 실어 보내므로 실패 응답인데도 `response.data.data` 에 `AiDraftCapacityExceededResponseDto` 가 들어 있다. `error.code` 로 분기해야 한다.
- 고객 케어는 실제 고객에게 나가는 메시지라 채널 선택지에서 `SNS_CARD` 를 제외했다 (`AiChannel#noticeSendable == false`).
- `contextHint` 는 빈 문자열 대신 `null` 로 보낸다 (백엔드에서 선택 항목).
- 초안 생성 응답은 201 CREATED 다.
