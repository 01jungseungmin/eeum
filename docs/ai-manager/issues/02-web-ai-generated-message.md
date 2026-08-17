## 📄 작업 유형
- [ ] 기능 개발
- [x] 화면 개발
- [ ] API 개발
- [ ] 문서 수정
- [ ] 버그 수정
- [ ] 리팩토링
- [ ] 테스트

## 🎯 작업 목적
AI 매니저의 고객 케어 / 마케팅 / 리뷰·문의 초안 생성 API는 **모두 `AiGeneratedMessageResponseDto` 를 반환**한다. 즉 "초안을 검토하고 → 수정하고 → 보내거나 예약하거나 취소한다"는 흐름이 전 도메인에서 동일하다.

따라서 각 도메인 화면을 만들기 전에 이 흐름을 담당하는 **공용 초안 검토 모달(`AiMessageDraftModal`)** 을 먼저 확정하고, 이를 검증할 수 있는 AI 생성 메시지 목록 화면(`/ai-manager/messages`)을 구현한다. 이후 고객 케어·마케팅·리뷰/문의 이슈는 draft 생성 API만 교체해 이 모달을 재사용한다.

## 📌 작업 내용
- `src/api/owner/aiMessageApi.js` 신설 — 목록/상세/수정/발송/예약/취소 + 공지 등록/공지 예약 (총 8개 엔드포인트)
- `src/constants/aiConstants.js` 보강 — 발송 상태별 배지 색상, `AI_EDITABLE_STATUSES`, `AI_TRANSITABLE_STATUSES` 추가
- `src/components/owner/ai/AiMessageStatusBadge.jsx` 신설 — DRAFT/REVIEWED/SENT/SCHEDULED/FAILED/CANCELLED 배지
- `src/components/owner/ai/AiMessageDraftModal.jsx` 신설 — **공용 초안 검토 모달**
  - 제목/문구 수정, 2000자 카운터, `originalContent` 로 되돌리기
  - 검토 후 보내기 / 예약 발송 / 취소
  - `type === 'NOTICE'` 인 경우 `notices/{id}/publish`, `notices/{id}/schedule` 로 분기
- `src/components/owner/ai/AiMessageRow.jsx` 신설 — 목록 행 (유형·상태·채널·예약/발송 시각)
- `src/pages/owner/main/AiMessagePage.jsx` 신설 — 유형 필터 탭 + 페이징 목록 + 모달 연결
- `App.jsx` 에 `/ai-manager/messages` 라우트, `MenuConfig.jsx` 에 `AI 생성 메시지` 메뉴 추가

## ✅ 완료 조건
- [ ] `/ai-manager/messages` 에서 AI 생성 메시지 목록이 페이징(20건)으로 조회된다
- [ ] 유형 탭(전체 + 9종) 전환 시 `type` 파라미터로 필터링되고 0페이지로 초기화된다
- [ ] 행 클릭 시 초안 검토 모달이 열리고 제목/문구를 수정해 저장할 수 있다
- [ ] **DRAFT 상태에서 바로 발송이 불가**한 백엔드 규칙에 맞춰, 보내기/예약 시 수정(PATCH)으로 REVIEWED 전환을 선행한다
- [ ] `type === 'NOTICE'` 인 메시지는 공지 등록/공지 예약 엔드포인트로 호출된다
- [ ] SENT/CANCELLED/FAILED 등 수정 불가 상태에서는 입력과 액션 버튼이 비활성화된다
- [ ] `AI_MESSAGE_ALREADY_SENT`, `AI_INVALID_SCHEDULE_TIME`, `AI_INVALID_CHANNEL` 등 실패 시 백엔드 메시지가 그대로 노출된다
- [ ] 발송/예약/취소 후 목록이 갱신되어 상태 배지가 바뀐다
- [ ] `npm run build` 성공

## 🔗 관련 문서 / API
- `docs/ai-manager/00-overview.md` 2-2 (실제 외부 발송 없이 DB 상태만 변경)
- Swagger: `OwnerAiGeneratedMessageController` — `/owner/ai-manager/generated-messages*`, `/owner/ai-manager/notices/{messageId}/*`
- DTO: `AiGeneratedMessageResponseDto`, `AiGeneratedMessageUpdateRequestDto`, `AiMessageScheduleRequestDto`
- 상태 전이 규칙: `domain/ai/entity/AiGeneratedMessage#edit / send / schedule / cancel`

## 📎 참고 사항
- **`validateTransitable()` 화이트리스트가 REVIEWED / SCHEDULED 뿐이다.** DRAFT는 `edit()` 으로 REVIEWED 전환 후에만 발송·예약·취소가 가능하다. 프론트에서 이 2단계를 감싸주지 않으면 첫 발송이 항상 `AI_INVALID_STATUS` 로 실패한다.
- 예약된 메시지를 수정하면 백엔드가 `scheduledAt` 을 null 로 만들고 REVIEWED 로 되돌린다. 수정 후에는 다시 보내기/예약이 필요하다.
- `scheduledAt` 은 `LocalDateTime` 이라 `datetime-local` 값(`yyyy-MM-ddTHH:mm`)에 `:00` 을 붙여 보낸다.
- 목록만 `Page<>` 응답이므로 `number → page` 매핑은 기존 `admin/main/InquiryManagementPage.jsx` 방식을 따랐다.
- 선행 이슈: `docs/ai-manager/issues/01-web-ai-manager-dashboard.md`
