# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 맥락

이 디렉토리(`frontend-web`)는 `eeum` 모노레포의 일부입니다. `eeum`은 지역 소상공인 O2O 플랫폼으로, 하나의 Spring Boot 백엔드를 세 클라이언트가 공유합니다: React Native 소비자 앱(`frontend-app`), Spring Boot API(`backend`), 그리고 이 프로젝트 — **사장(store owner) 웹 + 관리자(admin) 웹**. 두 역할 모두 이 단일 Vite/React SPA로 서비스됩니다.

## 명령어

```bash
npm run dev          # vite dev 서버 (:5173), /api와 /ws를 localhost:8080으로 프록시 (vite.config.js 참고)
npm run build         # 프로덕션 빌드
npm run lint          # eslint (flat config, react-hooks + react-refresh 플러그인)
npm run test           # vitest run (jsdom)
npm run test:watch    # vitest watch 모드
npm run preview        # 프로덕션 빌드 미리보기
```

단일 테스트 파일 실행: `npx vitest run src/hooks/useAiDraft.test.jsx`

테스트 커버리지가 거의 없습니다 (`src/hooks/useAiDraft.test.jsx`가 유일한 스펙 파일) — `npm run test`가 통과한다고 해서 변경이 안전하다고 단정하지 마세요.

### 로컬 백엔드로 붙여서 실행하기

`src/api/apiClient.js`가 읽는 `import.meta.env.VITE_API_URL`이 기본 상대경로(`/api`)를 덮어씁니다. **`npm run dev`가 실제로 로컬 백엔드를 바라보는지 확인하기 전에 `.env.local`부터 확인하세요** — 평소 프론트엔드 작업용으로 배포된 API(`https://eeum.life/api`)를 가리키도록 설정돼 있는 경우가 흔합니다. 로컬 백엔드로 강제하려면: `VITE_API_URL=/api npm run dev` (백엔드는 `:8080`에서 떠 있어야 함, vite.config.js가 `/api`를 `http://localhost:8080`으로 프록시하며 `/api` 접두사를 벗겨서 전달하고, `/ws`도 STOMP용으로 프록시합니다).

## 아키텍처

### 두 개의 역할, 하나의 라우터, 하나의 백엔드 계약

`src/App.jsx`에 모든 라우트가 직접 정의되어 있습니다 (라우트 단위 lazy 모듈 없음). 공개 라우트(`/login`, `/sign-up`, `/admin/login` 등)는 `MainLayout` 바깥에 있고, 나머지는 전부 그 하위에 중첩됩니다. `MainLayout`(`src/layouts/MainLayout.jsx`)이 사장님의 승인 상태 + 대시보드 스냅샷을 한 번만 불러와서 `<Outlet context={...}>`으로 내려주며, 하위 페이지들은 각자 따로 조회하지 않고 `useOutletContext()`로 받아서 씁니다 (`DashBoardPage.jsx` 참고). 사장 운영 라우트(상품, 주문, AI 매니저, 정산 등)는 여기에 더해 `ApprovalGuard`(`src/components/owner/ApprovalGuard.jsx`)로 한 번 더 감싸져 있어서, 매장의 `approvalStatus === 'APPROVED'`가 될 때까지 `/approval-status`로 리다이렉트합니다. 관리자 라우트는 승인 게이트가 없고, `sessionStorage`의 `role` 값만으로 구분됩니다.

`src/config/MenuConfig.jsx`가 사이드바 내비게이션(`OWNER_MENU_CONFIG` / `ADMIN_MENU_CONFIG`)과 `TopNavbar`에 표시되는 페이지 타이틀/서브타이틀(`findMenuByPath` 경유) 둘 다의 단일 소스입니다. 사이드바 최상위 항목이 아닌 페이지(예: AI 매니저 하위 페이지)는 `SUB_PAGE_CONFIG`에도 등록해야 하며, 안 그러면 navbar에 일반적인 제목만 표시됩니다. 새 라우트를 추가할 때는 여기도 같이 등록하세요.

### 인증/토큰 아키텍처 (로그인, 세션 복구, 401 처리를 건드리기 전에 읽을 것)

`src/api/apiClient.js`는 axios 인터셉터가 렌더링을 기다리지 않고 동기적으로 `Authorization` 헤더를 붙일 수 있도록, `accessToken`을 의도적으로 React 바깥(모듈 스코프 변수)에 둡니다. `AuthContext`(`src/contexts/AuthContext.jsx`)는 이를 React 쪽에서 감싸는 래퍼로, 리스너(`onTokenChange`)를 등록해서 axios의 401 인터셉터가 조용히 토큰을 재발급해도 React state(그리고 `accessToken`을 구독하는 `useChatSocket`의 SSE/WS 재연결 같은 것들)가 함께 갱신되게 합니다.

refreshToken은 **백엔드가 1회용으로 회전시킵니다** — 같은 토큰으로 `/auth/token/reissue`를 두 번 호출하면 세션이 무효화됩니다. 401 인터셉터와 `AuthContext`의 마운트 시점 `restoreSession()`이 둘 다 독립적으로 재발급을 시도할 수 있기 때문에, `reissueAccessToken()`(`apiClient.js`)이 두 호출자 간에 진행 중인 Promise 하나를 공유합니다. 이 함수를 거치지 않는 세 번째 재발급 호출자를 추가하면 이 가드가 막고 있던 레이스 컨디션이 재발합니다.

스토리지 분리는 의도적입니다: `accessToken` → 메모리만, `refreshToken` → `localStorage`(새로고침 후에도 유지), `role`/`my_store_id`/채팅방 개설 여부 → `sessionStorage`(로그아웃 시 탭 단위로 정리). `ProtectedRoute.jsx`와 `MainLayout` 둘 다 `/login`으로 보내기 전에 세션 복구를 시도할지 판단할 때, refreshToken이 *존재하는지*만 확인합니다.

### API 레이어

`src/api/owner/*.js` / `src/api/admin/*.js` 아래 도메인별로 파일이 하나씩 있고, 전부 공유되는 `apiClient` axios 인스턴스를 얇게 감싼 래퍼입니다. **파일마다 반환 형태가 일관되지 않습니다** — 일부는 axios 응답을 그대로 반환하고(페이징된 Spring `Page`라면 `res.data.data.content`로 접근), 일부는 래퍼 안에서 이미 `res.data`까지 풀어서 반환합니다. 형태를 짐작하지 말고 해당 파일을 직접 확인하세요. 모든 백엔드 응답은 `{ success, data, message, error, timestamp, traceId }`로 감싸져 있고, 페이징 엔드포인트는 그 `data` 안에 Spring Data `Page` 객체(`content`, `last`, `totalElements` 등)를 담습니다.

대부분의 목록 데이터에는 집계/요약 전용 백엔드 엔드포인트가 없습니다 — 합계가 필요한 페이지(예: `useSettlementData.js`)는 서버가 집계해줄 거라 기대하지 않고, bounded lookback/페이지 상한을 두고 클라이언트에서 직접 페이지를 순회하며 집계합니다. 비슷한 대시보드성 집계를 만들 때는 "전부 다 가져오기"가 아니라 이 패턴(상한을 둔 루프)을 따르세요.

### 실시간 기능

- 채팅: `useChatSocket.js`가 `/ws`로 STOMP 클라이언트를 엽니다. WebSocket URL은 `VITE_API_URL`의 origin에서 계산하고(없으면 `window.location`으로 폴백), `accessToken`이 바뀔 때마다 재구독합니다 — 위의 토큰 변경 브릿지가 여기서 중요한 이유입니다.
- 알림: `notificationApi.getSubscribeInfo()`를 통한 SSE, `Sidebar.jsx`에 연결되어 있습니다.

### AI 매니저 (`components/owner/ai/`, `pages/owner/main/Ai*.jsx`)

가장 규모가 크고 백엔드와 가장 밀접하게 엮인 기능 영역입니다. 건드리기 전에 알아둘 패턴 두 가지:
- **플랜 기반 기능 게이팅**: `constants/aiPlanFeatures.js` + `AiBasicUpgradeBanner.jsx`가 매장이 구독한 플랜(`AuthContext.aiPlanType`, 세션당 1회 조회)에 따라 AI 하위 기능을 제한합니다.
- **초안 → 검토 → 발송 흐름**: `useAiDraft.js`가 AI 메시지 생성을 감쌉니다. 백엔드가 상태 전이(`DRAFT → REVIEWED → SENT`)를 엄격하게 강제하므로, 사용자가 문구를 수정하지 않았더라도 "업데이트"를 명시적으로 호출하지 않고 발송하면 `AI_INVALID_STATUS`로 실패합니다. `useAiDraft` 위에 새 모달을 만들 때는 텍스트 수정 여부와 무관하게 항상 발송 전에 업데이트 엔드포인트를 먼저 호출해야 같은 버그를 반복하지 않습니다. 이 훅은 `AI_015`(초안 보관 개수 초과)도 처리하는데, 사용자에게 삭제 확인을 받은 뒤 `confirmDelete: true`로 재요청합니다.

### 알려진 함정: 대소문자를 구분하는 임포트

macOS 로컬 개발 환경은 대소문자가 다른 임포트/디렉토리(예: `components/admin/Category/` vs `components/admin/category/`)를 문제없이 통과시키지만, Linux 기반 CI 빌드는 이 부분에서 실패합니다. 특히 `components/admin/` 아래에서는 기존 대소문자 표기를 정확히 맞추세요.
