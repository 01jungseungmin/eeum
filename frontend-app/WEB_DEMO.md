# 웹 데모 배포 (공모전 심사용)

공모전 규정상 APK 직접 배포가 불가해, Expo 앱을 웹으로 export 해서 심사자가
설치·로그인 없이 브라우저에서 체험할 수 있게 한 경로다.

- 배포 URL: https://eeum-web-demo.vercel.app
- 네이티브 앱 빌드에는 영향이 없다. 웹 전용 동작은 전부 `.web.ts` 플랫폼 확장자와
  `isDemoLoginEnabled` 플래그 뒤에 있고, 데모 자격증명이 비면 꺼진다.

## 배포 방법

**정적 산출물만 올린다.** 저장소 루트를 Vercel에 그대로 물리면 실패한다 —
`frontend-app/api/*.ts`(API 클라이언트 코드)를 Vercel이 서버리스 함수로 오인해
Hobby 플랜의 함수 12개 제한에 걸린다.

```bash
cd frontend-app

# 1. 운영 설정으로 빌드. --clear 필수 — Metro 캐시가 남아 있으면
#    .env를 바꿔도 이전 환경변수가 그대로 번들에 박힌다.
npx expo export -p web --output-dir dist-web --clear

# 2. 배포용 후처리. 건너뛰면 아이콘이 전부 깨지고 동적 경로가 404가 된다 (아래 설명)
node scripts/prepare-web-deploy.mjs dist-web

# 3. 산출물 디렉터리에서 배포. link를 건너뛰지 말 것 (아래 설명)
cd dist-web
npx vercel link --yes --project eeum-web-demo --token <VERCEL_TOKEN>
npx vercel deploy --yes --prod --token <VERCEL_TOKEN>
```

**`vercel link`를 반드시 먼저 한다.** `--clear` 빌드가 `dist-web/.vercel`(프로젝트 링크)을
매번 지우기 때문에 `dist-web`은 항상 링크가 없는 상태다. 이때 `vercel deploy --yes`를
그냥 돌리면 물어보지 않고 **디렉터리 이름을 딴 `dist-web`이라는 새 프로젝트**를 만들어
거기에 올린다. 배포는 성공했다고 나오는데 심사자에게 준 URL은 옛날 빌드 그대로다.

### 2단계가 필요한 이유

`prepare-web-deploy.mjs`가 두 가지를 한다. 둘 다 `--clear` 빌드가 매번 없애므로 자동화했다.

**에셋 경로에서 `node_modules` 제거.** Metro는 의존성 에셋을
`dist-web/assets/node_modules/@expo/vector-icons/...`로 내보내는데, Vercel CLI는 경로에
`node_modules`가 들어간 파일을 업로드에서 **무조건** 제외한다 (`.vercelignore`의 `!` 부정
패턴으로도, `--archive`로도 안 된다). 그러면 폰트 요청이 SPA rewrite에 걸려 `index.html`을
받고 브라우저가 `OTS parsing error: invalid sfntVersion`을 내며 **아이콘이 전부 깨진다.**

**`dist-web/vercel.json` 생성.** 없으면 Vercel이 `package.json`도 없는 디렉터리에서
`npm ci`를 돌리다 배포가 실패하고, SPA rewrite가 빠져 `/used-trade/1` 같은 동적 경로가 404가 된다.

`vercel login`은 컴퓨터 이름이 ASCII가 아니면 실패한다(HTTP 헤더에 호스트명이 들어간다).
그 경우 https://vercel.com/account/tokens 에서 토큰을 만들어 `--token`으로 넘긴다.

## 환경변수

`.env`에 넣는다. `app.config.js`가 `dotenv.config()`로 `.env`를 먼저 읽기 때문에
`.env.local`이나 셸 환경변수는 무시된다.

| 변수 | 용도 |
|---|---|
| `EXPO_PUBLIC_API_URL` | `https://eeum.life/api` |
| `EXPO_PUBLIC_DEMO_EMAIL` / `_PASSWORD` | 데모 자동 로그인. 둘 다 있어야 켜진다 |
| `EXPO_PUBLIC_KAKAO_JS_KEY` | 지도 |
| `EXPO_PUBLIC_DEMO_PAYMENT_ENABLED` | 웹 PG 결제 on/off. `true`일 때만 켜진다 |

`EXPO_PUBLIC_` 값은 번들에 평문으로 박힌다. 데모 비밀번호는 공개된 것으로 간주하고
심사 종료 후 반드시 변경한다.

## 의존하는 서버 설정

- **CORS**: 배포 도메인이 백엔드 `cors.allowed-origins`(환경변수 `CORS_ALLOWED_ORIGINS`)에
  있어야 한다. 없으면 preflight가 403이라 화면은 뜨는데 데이터가 하나도 안 나온다.
- **카카오 지도**: Kakao Developers 콘솔의 웹 플랫폼 사이트 도메인에 배포 도메인을
  등록해야 SDK가 로드된다.
- **데모 계정**: 활동지역이 없으면 홈·중고거래가 빈 화면이 된다. 가입 후
  `/accounts/me/regions`로 지역을 추가하고 verify·primary까지 마쳐야 한다.

## PC에서 폰 화면처럼 보이기

`utils/mobileFrame.web.ts`가 진입점(`app/_layout.tsx`)에서 한 번 돈다. 두 가지를 같이 한다.

- `#root`를 430px로 묶고 양옆을 회색 배경 + 그림자로 둔다 (`@media (min-width: 431px)`에서만)
- `Dimensions.get`·`addEventListener`가 돌려주는 너비를 430px로 깎는다

두 번째가 핵심이다. 배너 캐러셀·상품/상점/중고 상세는 `Dimensions.get('window').width`로
이미지 크기를 직접 계산하므로, CSS로 틀만 줄이면 이미지가 브라우저 전체 너비로 그려져
틀 밖으로 삐져나간다. 여기서 한 번 보정해 두면 그 화면들은 고치지 않아도 된다.

폰으로 접속하면 화면이 430px보다 좁아 미디어 쿼리도 클램프도 걸리지 않는다.
네이티브는 `utils/mobileFrame.ts`(빈 구현)가 번들되므로 영향이 없다.

한계: 일부 `Modal`은 웹에서 페이지 전체 위에 떠서 어두운 배경막이 틀 밖까지 덮는다.
내용은 가운데에 뜨므로 사용에는 지장이 없다.

## 웹 결제

`app/order/checkout.tsx`는 네이티브에서 포트원 결제창을 `react-native-webview` 안에 띄우는데,
이 라이브러리는 웹을 지원하지 않는다 — 웹에서는 결제창 대신 빈 화면이 뜬다.
그래서 웹은 `utils/webPayment.web.ts`가 같은 포트원 브라우저 SDK를 **웹뷰 없이 직접** 부른다.

- redirect 주소도 플랫폼별로 갈린다 (`utils/paymentCompletion.ts`). 네이티브는 `eeum://payment/success`,
  웹은 `${location.origin}/payment/success` — 브라우저는 앱 스킴을 열지 못한다.
- PC는 결제창이 팝업으로 떠 `requestPayment`가 결과를 돌려주고 그 자리에서 검증한다.
  모바일 브라우저는 페이지가 통째로 넘어갔다 `/payment/success`로 돌아와 이어받는다.
- 백엔드는 손대지 않았다. 검증(`POST /payments/verify`)과 Webhook은 앱과 같은 경로다.

**기본은 꺼져 있다.** `EXPO_PUBLIC_DEMO_PAYMENT_ENABLED=true`가 아니면 체크아웃이
*주문을 만들기 전에* "체험판에서는 결제까지는 안 돼요" 안내를 띄우고 멈춘다 —
결제창에서 막으면 재고가 줄고 미결제 주문이 남기 때문이다. 현장결제는 PG를 거치지 않아
이 값과 무관하게 항상 동작하므로, 결제를 꺼둬도 주문 흐름 자체는 시연할 수 있다.

켜기 전에 `EXPO_PUBLIC_PORTONE_DEFAULT_KEY`가 포트원 콘솔의 **테스트 채널** 키인지
반드시 확인한다. 실결제 채널이면 심사자가 버튼을 누르는 순간 실제 카드 결제가 된다.

## 웹에서 막아둔 경로

- 회원가입(버튼 + `/signup` 직접 진입): 투표자가 실명·연락처를 입력하면 수집한
  개인정보의 보호 책임이 생긴다
- 회원탈퇴: 데모 비밀번호가 번들에 노출돼 있어 누구나 계정을 지울 수 있다
- 카카오·네이버 로그인: 네이티브 SDK라 웹에 존재하지 않는다
