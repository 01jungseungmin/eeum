<div align="center">

<!-- (로고 이미지 필요 — 예: docs/assets/logo.png) -->
<img src="docs/assets/logo.png" alt="이음 로고" width="120" />

# 이음 (Eeum)

**지역 소상공인과 소비자를 잇는 플랫폼**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![React Native](https://img.shields.io/badge/React_Native-Expo-61DAFB?style=flat-square&logo=react&logoColor=black)](https://expo.dev/)
[![React](https://img.shields.io/badge/React-Vite-61DAFB?style=flat-square&logo=react&logoColor=black)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)

</div>

---

## 📖 프로젝트 소개

**이음**은 지역 소상공인과 소비자를 연결하는 O2O(Online to Offline) 플랫폼입니다.  
소비자는 주변 가게를 탐색하고 상품을 주문·예약하며, 소상공인은 가게를 관리하고 고객과 소통할 수 있습니다.  
중고거래·커뮤니티·채팅 기능으로 지역 기반 연결망을 형성합니다.

> **3개 클라이언트** — 소비자용 모바일 앱 · 사장용 웹 · 관리자용 웹이 하나의 백엔드 API를 공유합니다.

---

## ⚙️ 백엔드 API

### 기술 스택

| 분류           | 기술                                |
| -------------- | ----------------------------------- |
| Language       | Java 17                             |
| Framework      | Spring Boot 4.0.6                   |
| ORM            | Spring Data JPA + QueryDSL 5.1      |
| Security       | Spring Security + JWT (jjwt 0.12.6) |
| Real-time      | WebSocket (STOMP)                   |
| Cache / Lock   | Redis 7                             |
| Database       | MySQL 8.0                           |
| Push           | Firebase Cloud Messaging (FCM)      |
| Payment        | PortOne                             |
| Object Storage | AWS S3                              |
| OAuth          | Kakao OAuth 2.0                     |
| API Docs       | SpringDoc OpenAPI 3.0               |
| Build          | Gradle                              |

---

## 📱 모바일 앱 (소비자용)

<!-- (앱 스크린샷 필요 — 홈, 지도, 주문, 채팅, 마이페이지 화면 각 1장씩) -->
<div align="center">
  <img src="" alt="홈" width="160" />
  <img src="" alt="지도" width="160" />
  <img src="" alt="주문" width="160" />
  <img src="" alt="채팅" width="160" />
  <img src="" alt="마이페이지" width="160" />
</div>

### 주요 화면

| 탭 / 화면         | 기능                                   |
| ----------------- | -------------------------------------- |
| 🏠 **홈**         | 주변 스토어 탐색, 이벤트 상품, 추천    |
| 🗺️ **지도**       | GPS 기반 주변 가게 지도 탐색           |
| 💬 **채팅**       | 1:1 · 그룹 채팅 (실시간 WebSocket)     |
| 🏘️ **커뮤니티**   | 게시글 작성·댓글·좋아요                |
| 👤 **마이페이지** | 주문내역, 예약내역, 찜 목록, 알림 설정 |
| 🛒 **주문·결제**  | 장바구니 → 주문 → PortOne 결제         |
| 📅 **방문 예약**  | 시간 슬롯 선택 및 예약 확인            |
| 🔔 **알림**       | FCM 푸시 알림 수신                     |
| 🔍 **검색**       | 스토어·상품 통합 검색                  |

### 기술 스택

| 분류        | 기술                                              |
| ----------- | ------------------------------------------------- |
| Framework   | React Native + Expo SDK                           |
| Navigation  | Expo Router (파일 기반 라우팅) + React Navigation |
| 소셜 로그인 | Kakao Login, Naver Login                          |
| 위치        | expo-location (GPS)                               |
| 알림        | expo-notifications (FCM)                          |
| 이미지      | expo-image, expo-image-picker                     |
| HTTP        | Axios                                             |
| Storage     | expo-secure-store                                 |

---

## 🖥️ 사장 웹 (사장 고객용)

<!-- (웹 스크린샷 필요 — 대시보드 화면 1장) -->
<div align="center">
  <img src="docs/assets/web-dashboard.png" alt="대시보드" width="700" />
</div>

### 주요 화면

| 페이지             | 기능                                                 |
| ------------------ | ---------------------------------------------------- |
| 📊 **대시보드**    | 매출 차트(Recharts), 최근 주문, 인기 상품, 최근 리뷰 |
| 🏪 **스토어 관리** | 가게 정보·영업시간·이미지·공지 편집                  |
| 🍽️ **상품 관리**   | 메뉴·판매 상품 등록, 카테고리·옵션 설정              |
| ⚡ **이벤트 관리** | 플래시 세일 이벤트 등록·관리                         |
| 📦 **주문 관리**   | 주문 수락·거절·완료 처리                             |
| 📅 **예약 관리**   | 방문 예약 슬롯 설정 및 예약 목록 조회                |
| ⭐ **리뷰 관리**   | 리뷰 조회, 답변 작성, 신고 처리                      |
| 👥 **고객 관리**   | 고객별 주문내역·통계 조회                            |
| ✅ **승인 현황**   | 사업자 등록 및 입점 승인 진행 상태 확인              |

### 기술 스택

| 분류      | 기술              |
| --------- | ----------------- |
| Framework | React 18 + Vite   |
| Routing   | React Router DOM  |
| 스타일    | Styled Components |
| 차트      | Recharts          |
| 아이콘    | Lucide React      |
| HTTP      | Axios             |

### 레이어 구조

```
com.eeum.eeum
├── api/              ← Controllers (REST 엔드포인트)
├── application/      ← Services, DTOs, Mappers, Schedulers
├── domain/           ← JPA Entities, Repositories, Enums, Domain Events
├── infrastructure/   ← 외부 연동 어댑터 (FCM, SSE)
├── config/           ← Spring 설정 빈
├── security/         ← JWT 필터 체인, UserDetails
├── common/           ← BaseEntity, RedisUtil, RedisLockService, SecurityUtil
└── exception/        ← ErrorCode, 예외 클래스, GlobalExceptionHandler
```

---

## 🏗️ 시스템 아키텍처

<!-- (아키텍처 다이어그램 이미지 필요 — 전체 시스템 구성도) -->
<div align="center">
  <img src="docs/assets/architecture.png" alt="시스템 아키텍처" width="800" />
</div>

---

## 📁 프로젝트 구조

```
eeum/
├── backend/            ← Spring Boot API 서버
├── frontend-app/       ← React Native (Expo) 소비자 모바일 앱
├── frontend-web/       ← React 사장·관리자 웹
├── docs/               ← 설계 문서 (SDD 등)
├── docker-compose.yml  ← 로컬 인프라 (MySQL, Redis)
└── .env.example        ← 환경 변수 예시
```

---

## 🚀 시작하기

### 사전 요구사항

- Java 17+
- Docker & Docker Compose
- Node.js 18+

### 1. 저장소 클론

```bash
git clone https://github.com/your-org/eeum.git
cd eeum
```

### 2. 환경 변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 필수 값 입력
```

<details>
<summary>필수 환경 변수 목록</summary>

| 변수                                                  | 설명                   |
| ----------------------------------------------------- | ---------------------- |
| `JWT_SECRET`                                          | HS256 서명 키          |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`              | MySQL 접속 정보        |
| `REDIS_HOST` / `REDIS_PORT`                           | Redis 접속 정보        |
| `MAIL_USERNAME` / `MAIL_PASSWORD`                     | Gmail SMTP             |
| `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` / `AWS_S3_BUCKET` | AWS S3                 |
| `PORTONE_API_SECRET` / `PORTONE_WEBHOOK_SECRET`       | PortOne 결제           |
| `KAKAO_REST_API_KEY`                                  | 카카오 OAuth           |
| `FCM_PROJECT_ID` / `FCM_SERVICE_ACCOUNT_KEY_PATH`     | Firebase FCM           |
| `NTS_BUSINESS_SERVICE_KEY`                            | 국세청 사업자 조회 API |

</details>

### 3. 로컬 인프라 실행 (MySQL + Redis)

```bash
docker-compose up -d
```

### 4. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

Swagger UI → `http://localhost:8080/swagger-ui/index.html`

### 5. 모바일 앱 실행

```bash
cd frontend-app
npm install
npx expo start
```

iOS 시뮬레이터 또는 Expo Go 앱으로 실행합니다.

### 6. 사장·관리자 웹 실행

```bash
cd frontend-web
npm install
npm run dev
```

브라우저에서 `http://localhost:5173` 접속 후 사장 로그인을 진행합니다.

---

## 🗄️ ERD

<!-- (ERD 이미지 필요 — DB 설계 다이어그램) -->
<div align="center">
  <img src="docs/assets/erd.png" alt="ERD" width="900" />
</div>

---

## 📡 API 명세

서버 실행 후 Swagger UI에서 전체 API를 확인할 수 있습니다.

| 태그         | 경로                    | 주요 클라이언트 |
| ------------ | ----------------------- | --------------- |
| Auth         | `/auth/**`              | 앱·웹 공통      |
| Store        | `/stores/**`            | 앱              |
| Order        | `/orders/**`            | 앱              |
| Reservation  | `/reservations/**`      | 앱              |
| Chat         | `/chat-rooms/**`, `/ws` | 앱              |
| Community    | `/community/**`         | 앱              |
| Notification | `/notifications/**`     | 앱              |
| Report       | `/reports/**`           | 앱              |
| Owner        | `/owner/**`             | 사장 웹         |
| Admin        | `/admin/**`             | 관리자 웹       |

---

## 🔑 핵심 설계 패턴

### 동시성 제어

주문 생성, 재고 차감, 예약 처리는 Redis 분산 락으로 동시성을 보장합니다.

```java
redisLockService.executeWithLock(LockKeys.ORDER + orderId, () -> { ... });
```

### 도메인 이벤트

FCM 푸시·SSE 알림은 도메인 이벤트를 통해 트랜잭션과 분리됩니다.

```java
eventPublisher.publishEvent(new OrderPaidEvent(order));
// @TransactionalEventListener(AFTER_COMMIT) + @Async
```

### 멱등성 보장

- PortOne Webhook: Redis + DB Unique 제약으로 중복 결제 처리 방지
- ChatRoom 생성: 동일 참여자 조합의 중복 방 생성 방지

---

## 🧪 테스트

```bash
cd backend

# 전체 테스트 실행
./gradlew test

# 단일 클래스 테스트
./gradlew test --tests "com.eeum.eeum.ClassName"

# 클린 빌드
./gradlew clean build
```

---

## 👥 팀원

| 이름   | 역할          |
| ------ | ------------- |
| 정승민 | 팀장 · 백엔드 |
| 김재현 | 프론트엔드    |
| 최정원 | 프론트엔드    |
