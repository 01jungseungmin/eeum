## 📄 작업 유형
- [ ] 기능 개발
- [x] 화면 개발
- [ ] API 개발
- [ ] 문서 수정
- [ ] 버그 수정
- [ ] 리팩토링
- [ ] 테스트

## 🎯 작업 목적
사장 웹(frontend-web)에 AI 매니저 1차 MVP를 붙이기 위한 **첫 단계**로, 진입점이 되는 AI 매니저 메인 대시보드 화면을 구현한다.

백엔드 `GET /owner/ai-manager/dashboard` 는 고객 케어 / 리뷰·문의 / 이벤트 성과 / 생활권 매칭 / 운영 위험 / AI 활동 요약을 한 번에 내려주는 허브 API이므로, 이 화면이 있어야 이후 세부 도메인 화면(고객 케어, 메시지, 마케팅, 생활권, 운영 위험, 플랜, 챗봇)으로 분기할 수 있다.

또한 이후 모든 AI 화면이 공통으로 사용할 **enum 라벨 상수 + 공통 카드 컴포넌트 + API 모듈 컨벤션**을 이 이슈에서 함께 확정한다. (의존성 최상위)

## 📌 작업 내용
- `src/constants/aiConstants.js` 신설 — 백엔드 `domain/ai/enums` 의 AiCareType / AiRiskLevel / AiMessageStatus / AiMessageType / AiChannel / AiTone / AiPlanType 한글 라벨 매핑 및 위험도 색상 팔레트 정의
- `src/api/owner/aiManagerApi.js` 신설 — `GET /owner/ai-manager/dashboard`, `GET /owner/ai-manager/activities/summary`, `GET /owner/ai-manager/plans` (기존 `xxxApi` 객체 컨벤션 준수)
- `src/components/owner/ai/` 신설 및 공통 컴포넌트 구현
  - `AiSectionCard` — 아이콘/제목/부제/우측 액션 슬롯을 가진 섹션 카드 (이후 전 AI 화면 재사용)
  - `AiStatGrid` — 지표 그리드, `null` 값은 `-` 로 표시
  - `AiRiskBadge` — 운영 위험 신호(NORMAL/CAUTION/WARNING) 배지
  - `AiCareCardList` — 고객 케어 카드 3종 요약
- `src/pages/owner/main/AiManagerPage.jsx` 신설 — 히어로 배너(보고 기준 시각 + 오늘 처리할 항목 수), 개인정보 안내 문구, 6개 요약 섹션 렌더링 및 상세 화면으로의 네비게이션 연결
- `App.jsx` 에 `/ai-manager` 라우트 추가 (`ApprovalGuard` 내부 배치)
- `config/MenuConfig.jsx` 의 `OWNER_MENU_CONFIG` 에 `AI 매니저` 그룹 추가

## ✅ 완료 조건
- [ ] 사이드바에 `AI 매니저` 메뉴가 노출되고 클릭 시 `/ai-manager` 로 이동한다
- [ ] 승인(APPROVED)되지 않은 사장 계정은 `ApprovalGuard` 에 의해 접근이 차단된다
- [ ] `GET /owner/ai-manager/dashboard` 응답의 7개 필드(reportedAt, todoCount, privacyNotice, customerCareSummaries, reviewInquirySummary, eventPerformanceSummary, localMatchScore, operationRiskSummary, activitySummary)가 모두 화면에 반영된다
- [ ] `localMatchScore`, `productViewCount`, `orderConversionRate`, `newCustomerRatio` 등이 `null` 인 경우 하드코딩 숫자가 아닌 빈 상태 문구/`-` 로 표시된다 (docs/ai-manager 2-3 원칙)
- [ ] 로딩 / 조회 실패 상태가 각각 별도 UI로 처리된다
- [ ] 기존 사장 웹 디자인 토큰(배경 `#f8f9fa`, 카드 `white`+`radius 16px`+`1px solid #eef0f2`, 포인트 `#00a651`/`#1c5335`)을 그대로 따른다
- [ ] `npm run build` 성공

## 🔗 관련 문서 / API
- `docs/ai-manager/00-overview.md` (1차 MVP 개발 원칙)
- `docs/ai-manager/01-dashboard.md` (메인 대시보드 명세)
- Swagger: `OwnerAiDashboardController` — `GET /owner/ai-manager/dashboard`, `/activities/summary`, `/plans`
- DTO: `AiManagerDashboardResponseDto`, `CustomerCareSummaryDto`, `ReviewInquirySummaryDto`, `EventPerformanceSummaryDto`, `OperationRiskSummaryDto`, `ActivitySummaryDto`

## 📎 참고 사항
- 문서(`01-dashboard.md`)에는 `/api/owner/...` 로 적혀 있으나 **실제 컨트롤러 매핑은 `/owner/ai-manager/...`** 이다. 기존 프론트 `apiClient` 도 `/api` 프리픽스를 쓰지 않으므로 `/owner/ai-manager/...` 로 호출한다.
- 이 이슈는 후속 AI 매니저 작업의 **선행 이슈**다. 이후 순서:
  1. AI 생성 메시지 초안 모달 + `/ai-manager/messages` (고객 케어·마케팅·리뷰/문의가 모두 `AiGeneratedMessageResponseDto` 를 반환하므로 모달 1개를 공용으로 사용)
  2. AI 고객 케어 → 3. 리뷰/문의 자동 대응 → 4. 마케팅/이벤트 성과 → 5. 운영 위험/절감 계획/전력 리포트 → 6. 생활권 매칭 → 7. 플랜/결제 → 8. 챗봇 위젯
- 초안 생성 계열 API는 플랜 한도 초과 시 `AiDraftCapacityExceededResponseDto` 를 별도로 내려주므로, 후속 이슈에서 업그레이드 유도 처리 필요.
- `GET /generated-messages` 만 `Page<>` 페이징 응답이고 나머지 목록 API는 배열이다.
- `POST /owner/ai-manager/test/fcm` 은 개발용이라 웹 UI 대상에서 제외한다.
- `GET /ai-exposures/*` 는 앱(공개) 전용이라 사장 웹에서는 사용하지 않는다.
