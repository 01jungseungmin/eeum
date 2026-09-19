---
name: new-feature
description: 사장/관리자 웹에 새 페이지나 기능을 추가할 때, 이 프로젝트 컨벤션에 맞춰 api/constants/hooks/components/pages 코드를 어떻게 나누고 라우팅·메뉴에 등록할지 안내. "새 페이지 만들어줘", "기능 추가해줘", "~ 관리 화면 구현해줘" 같은 요청에 사용한다.
---

새 화면/기능을 만들 때는 아래 순서와 파일 배치를 그대로 따르세요. `feat/web-owner-sales-settlement` 브랜치의 매출/정산 기능(`SalesSettlementPage`)이 이 모든 패턴을 담고 있는 실제 예시이니, 애매하면 그 코드를 참고하세요.

## 1. 파일을 만드는 순서

기능 하나를 추가할 때 보통 아래 순서로 파일이 생깁니다 (필요 없는 단계는 건너뛰되, 순서는 유지):

1. **API 래퍼** — `src/api/{owner|admin}/{domain}Api.js`
   - `apiClient`(`src/api/apiClient.js`)를 얇게 감싸는 함수 모음. 도메인 하나당 파일 하나.
   - 이 프로젝트 안에서도 반환 형태가 갈립니다: axios 응답을 그대로 반환(`apiClient.get(...)`)하거나, 래퍼 안에서 `.then((res) => res.data)`로 이미 풀어서 반환하거나. **새로 만들 때는 그대로 반환하는 쪽(`settlementApi.js`, `inquiryApi.js` 스타일)을 기본으로 쓰세요** — 호출부에서 `res.data.success && res.data.data...` 형태로 명시적으로 언랩하는 게 이 코드베이스에서 더 흔합니다.
2. **상태 상수** (백엔드 enum을 다루는 기능이면) — `src/constants/{domain}Constants.js`
   - 백엔드 enum 값 → 한글 라벨 매핑을 여기 모아둡니다 (`settlementConstants.js` 참고). 컴포넌트 안에 하드코딩하지 마세요.
3. **데이터 훅** (페이지 하나에서 API 여러 개를 조합하거나, 클라이언트 집계가 필요하면) — `src/hooks/use{Feature}.js`
   - 백엔드에 요약/집계 전용 엔드포인트가 없는 경우가 많습니다. `useSettlementData.js`처럼 **상한을 둔 페이지 순회**(lookback 기간 + 최대 페이지 수)로 필요한 만큼만 모아서 클라이언트에서 집계하세요. "전부 다 가져오기"는 금지.
   - 페이지 하나에서만 쓰는 단순 fetch는 훅으로 안 빼고 `TopProducts.jsx`처럼 컴포넌트 안 `useEffect`로 두어도 됩니다. 여러 곳에서 재사용되거나 로직이 복잡할 때만 훅으로 분리하세요.
4. **하위 컴포넌트** — `src/components/{owner|admin}/{domain}/*.jsx`
   - 탭 하나, 표 하나, 배너 하나, 모달 하나 단위로 파일을 쪼갭니다 (`RevenueOverviewTab.jsx`, `WeeklySettlementTab.jsx`, `SettlementPendingBanner.jsx` 참고).
   - 스타일은 각 파일 안에 `styled-components`로 인라인 — 전역 디자인 시스템 없음. 아래 "스타일 컨벤션" 참고.
5. **페이지** — `src/pages/{owner|admin}/main/{Feature}Page.jsx`
   - 요약 카드 + 탭/섹션을 조립하는 얇은 조립 컴포넌트. 데이터 훅을 호출하고 하위 컴포넌트에 내려주는 역할만 합니다.
6. **라우트 등록** — `src/App.jsx`
   - 사장 쪽 운영 화면(입점 승인 이후에만 봐야 하는 화면)이면 `<Route element={<ApprovalGuard />}>` 블록 **안**에 추가하세요. 승인 전에도 보여야 하는 화면(`/approval-status` 같은)은 그 블록 **밖**, `MainLayout` 안에 둡니다. 관리자 라우트는 `ApprovalGuard`와 무관하게 별도 블록에 있습니다.
7. **메뉴 등록** — `src/config/MenuConfig.jsx`
   - 사이드바에 노출할 최상위 메뉴면 `OWNER_MENU_CONFIG`/`ADMIN_MENU_CONFIG`의 해당 `group.items`에 추가.
   - 사이드바에는 안 뜨는 하위/상세 페이지(예: AI 매니저 상세 화면들)면 `SUB_PAGE_CONFIG`에 `path` 키로 등록 — 안 하면 `TopNavbar` 제목이 "상세 정보"로만 뜹니다.

## 2. 스타일 컨벤션 (기존 화면과 어긋나지 않으려면)

- 카드: `background: white; border: 1px solid #f0f0f0; border-radius: 16px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.02);`
- 브랜드 그린: `#2d5a43` (강조/활성 상태), 짙은 텍스트 `#262626`, 보조 텍스트 `#8c8c8c`, 옅은 보더 `#f0f0f0`.
- 요약 카드(숫자+아이콘 카드)는 새로 만들지 말고 `components/owner/dashboard/DashBoardCard.jsx`를 재사용하세요 (`title`/`value`/`unit`/`icon`/`iconBg`/`iconColor`/`trendText`/`subText` props).
- 탭: 얇은 pill 버튼 그룹, 활성 탭은 배경 `#2d5a43` + 흰 글씨, 비활성은 투명 배경 + `#595959` 글씨.
- 표: 헤더는 연회색 텍스트(`#8c8c8c`)에 보더만, 행은 `border-bottom: 1px solid #f5f5f5`. 상태는 pill 뱃지(`border-radius: 999px`)로 표시.
- 차트: `recharts`의 `AreaChart` + 그라디언트 fill, 라인 색은 브랜드 그린. Y축 금액 눈금은 만원 단위로 반올림하되, **값이 작을 때(10만원 미만) 소수 첫째 자리까지 표기**해서 "0만/1만"이 중복 표시되는 걸 피하세요 (`RevenueOverviewTab.jsx`의 `formatManwonTick` 참고).
- 로딩/빈 상태: 가운데 정렬된 연회색(`#bfbfbf`) 안내 문구. 스피너 컴포넌트 따로 없음.
- 백엔드에 아직 없는 기능(버튼은 있는데 API가 없는 경우)은 목업 데이터로 채우지 말고 `WithdrawalTab.jsx`처럼 "준비 중" 안내 + 대안 동선(다른 페이지로 연결)을 보여주세요.

## 3. 마무리 전 체크리스트

- `npm run lint` 0 error, `npm run build` 성공.
- 실사용 검증이 필요하면(UI 변경이면 사실상 항상) `.env.local`이 프로덕션 API를 가리키고 있을 수 있으니 `VITE_API_URL=/api npm run dev`로 로컬 백엔드에 붙여서 확인하세요.
- 새 디렉토리/파일명을 만들 때 기존 대소문자 표기(특히 `components/admin/` 하위)를 그대로 맞추세요 — macOS 로컬에서는 안 걸리지만 Linux CI 빌드가 대소문자 불일치로 실패합니다.
- 백엔드 API가 프론트가 원하는 필드를 다 안 주는 경우(예: 주문 상세에 고객명/상품명이 없는 등)가 있습니다. 백엔드 DTO까지 확장할지, 프론트에서 가능한 데이터로만 구성할지는 **추측하지 말고 사용자에게 먼저 확인**하세요 — 작업 범위와 소요 시간이 크게 달라집니다.
