# 3단계 계정·인증·권한 검토 원장

검토일: 2026-09-20. 기준 커밋: `3aa680693824c63a719453d7387f57f852232493`.
2026-09-20에는 읽기 전용 검토를 수행했다. 아래 원본 지적은 당시 기준이며, 2026-09-22 수정 상태는 다음 표를 우선한다.
사용자 요청에 따라 Gradle, MySQL/Redis 통합 테스트, 외부 서비스 실호출은 실행하지 않았다.
초기 검토 결론(수정 전): **3단계 통과 전. Major 8건, Minor 2건, 정책 보류 2건.** 이는 정적 코드 경로로 확인한 지적이며 실행 재현 결과가 아니다.

## 2026-09-22 수정 상태

수정 기준 HEAD: `f3f92da2`. 사용자가 수정 요청을 승인한 범위에서 작업했다.
Gradle/컴파일/외부 API 실호출/통합 테스트는 실행하지 않았다. 아래 `수정됨`은 실행 검증 통과를 의미하지 않는다.

| ID | 현재 상태 | 반영 사항 |
| --- | --- | --- |
| M-01 | 수정됨 | 가입·번호 변경·심사 요청에 서버 사업자 검증. 외부 검증 후 별도 DB 트랜잭션에서 정보 일치 재검사. 검증 시각/대표자명을 저장하고 번호 변경 시 무효화. 관리자 승인도 증빙 확인 |
| M-02 | 수정됨 | 로그아웃은 재발급 락 안에서 계정 DB 잠금·현재 토큰 세대·refresh 일치 확인 후 BEFORE_COMMIT 세대 회수. 해당 계정의 기존 모든 토큰을 회수하는 동작으로 명시 |
| M-03 | 수정됨 | 모든 JWT 발급마다 UUID jti 추가 |
| M-04 | 수정됨 | 최신 DB 세대보다 오래된 Redis 토큰만 compare-and-delete. 불일치한 refresh 제시만으로 최신 refresh를 삭제하지 않음 |
| M-05 | 재검토 통과 | 주문 생성과 활동지역 쓰기에 Account 잠금 후 assertWritable 적용. 주문은 현재 상점도 잠금 조회하여 폐점 상점의 주문 생성을 차단 |
| M-06 | 수정됨 | 비동기 FCM 발송 직전 계정 ACTIVE 및 현재 토큰 일치 확인. 외부 전송 시작 후의 회수까지 보장하는 것은 아님 |
| M-07 | 지적됨 | 보존 경로는 구현됐으나 지급 완료 뒤에도 PAID 결제가 남아 계좌 파기가 차단됨. 아래 최종 재검토 R3-01 참조 |
| M-08 | 수정됨 | Kakao access_token_info의 앱 ID·사용자 ID·잔여 유효기간 검사 후 user/me 사용자 ID 대조. 로그인과 재인증 모두 적용 |
| m-01 | 수정됨 | Kakao 이메일 유효·인증 플래그를 확인하고 미확인 이메일은 저장 입력에서 제외. OAuth Account의 이메일 인증 상태도 실제 결과 반영 |
| m-02 | 수정됨 / 일부 지적 정정 | SMTP·재인증 OAuth 경로의 전체 트랜잭션 제거, 필요한 계정 조회만 AuthAccountReader의 read-only 트랜잭션 사용. 기존 관리자 위치 조회는 외부 API가 아니라 Region/Location DB 조회였으므로 이 부분 지적은 철회 |
| P-01 | 재검토 통과 | 본인 탈퇴는 미완료 주문·방문예약·중고 예약·정산·취소 작업이 있으면 409으로 차단. 관리자 강제 탈퇴는 해당 이력을 보존하고 `FORCE_WITHDRAW` 감사 이력을 남김 |
| P-02 | 지적됨 | 정책은 사용자 확정 완료. 지급 완료 후 파기 구현은 R3-01로 재개방. 전체 법적 보존기간·백업 정책은 이번 수정으로 확정하지 않음 |

### 배포·호환성

- `V28__add_owner_business_verification.sql`, `V29__add_force_withdrawal_audit_action.sql` 적용이 필요하다. 기존 입력만 있는 사장 정보를 임의로 검증 완료로 backfill하지 않는다. 기존 미승인 신청은 사업자 정보 재저장 또는 심사 재요청으로 서버 검증을 받아야 하며, 증빙 없는 기존 심사 대기 건은 바로 승인할 수 없다.
- `KAKAO_APP_ID`에 서비스의 Kakao 앱 숫자 ID를 설정해야 한다. REST API 키와 다른 값이다. 미설정/0이면 Kakao 로그인을 거절한다. 요청/응답 필드나 엔드포인트는 변경하지 않았다.
- 로그아웃은 DB tokenVersion을 올리므로 해당 계정의 이전 기기 토큰도 회수된다. 현재 refresh 저장 구조가 계정당 하나라는 기존 계약에 맞춘다.
- 지연된 실시간 세션 종료 중계도 현재 DB 상태·세대와 대조하고 연결 식별자로 조건부 종료한다. 새 로그인으로 연결된 현재 세대는 유지한다.
- 본인 탈퇴는 진행 중 주문·예약·정산을 먼저 완료해야 한다. 관리자 강제 탈퇴는 거래·정산 행과 지급계좌를 미지급 상태가 끝날 때까지 보존하며, 관리자 이력에서 복구 대상을 추적한다.

### 추가·보강 테스트 (미실행)

- `TokenGenerationRegressionTest`: JWT 고유성, 지연 정리에서 현재 세대 보존, 원자적 조건부 삭제.
- `AccountLogoutServiceTest`: 현재 세대 회수 이벤트, 이전 세대 로그아웃 거절.
- `AccountStageThreeRegressionIntegrationTest`: MySQL/Redis에서 로그아웃 후 Redis 회수정보 유실 시 JWT 거절, 실제 Account 행 잠금 대기를 관찰하는 탈퇴/주문 경합.
- `AccountTokenCleanupLockIntegrationTest`: 실제 계정 fixture, 락 획득 latch, 늦은 이벤트 이후 새 일회용 토큰 보존.
- `OAuthServiceContractTest`: 로컬 HTTP Stub으로 앱 불일치·재인증·미인증/정상 이메일 계약.
- `OwnerBusinessVerificationTest`, `AuthServiceOwnerSignupTest`: 입력만으로 검증 완료 금지, 번호 변경/오래된 증빙 거절, 사업자 검증 실패 시 가입 미시작.
- `PushEligibilityReaderTest`: 탈퇴 및 토큰 교체 뒤 대기 FCM 미발송.
- `AccountWithdrawalGuardTest`, `AccountWithdrawalProcessorTest`: 본인 탈퇴의 미완료 거래 409 차단과 관리자 강제 탈퇴 경로의 분리.
- `SettlementAccountDeleteServiceTest`, `AccountCleanupServiceTest`, `AccountCleanupSchedulerTest`: 미지급 계좌 보존과 지급 완료 후 일일 재검사 파기.
- `RealtimeSessionRevocationTest`: 지연된 종료 중계가 새 세대의 WebSocket/SSE 연결을 유지.
- 기존 사장 승인/사업자번호/토큰 정리/주문 테스트의 fixture와 기대 동작을 수정 계약에 맞춤.

최종 정적 재검토에서 아래 결함을 확인했으므로 **3단계 미완료**다. 앞선 Critical/Major 없음 및 Gradle만 남았다는 판정은 철회한다. Gradle·MySQL/Redis 통합·동시성 테스트는 사용자 실행 대기이며 이번 검토에서도 실행하지 않았다.

### 최종 재검토 — 미해결 결함

1. **R3-01 / Major / 지적됨 — 지급 완료된 계좌가 계속 보존됨.** `WithdrawalObligationRepository.requiresSettlementAccount()`는 원장·주간 정산이 모두 완료돼도 온라인 Payment가 PAID 또는 PARTIALLY_REFUNDED이면 true다. `ManualSettlementPayoutService.complete()`와 `completeHandover()`는 OwnerRevenue를 SETTLED, WeeklySettlement를 COMPLETED로 전이시키지만 결제 상태는 변경하지 않는다. 정상 지급 완료 이력이 한 건만 있어도 익명화 및 후속 스케줄러 모두 계좌 삭제를 건너뛴다. 보존 조건에서 결제 대기·원장 누락·실제 미지급 채무를 완료된 지급과 구분해야 한다. 실제 저장된 PAID + SETTLED + COMPLETED 조합의 계좌 파기 회귀 테스트가 필요하다.
2. **R3-02 / Major / 지적됨 — 빈 장바구니 주문이 500으로 실패함.** `OrderService.createOrder()`는 CART_EMPTY 검사 전에 `cart.getStore().getStoreId()`를 호출한다. `Cart.create()`와 `Cart.clear()`는 store를 null로 두므로 정상적인 빈 장바구니 또는 주문 성공 뒤 재요청에서 NullPointerException이 발생한다. 상점 참조 전에 빈 장바구니를 거절하고, 신규 빈 장바구니 및 주문 완료 후 재요청에 대한 회귀 검증이 필요하다.

이번 요청은 최종 검토이므로 애플리케이션 코드는 변경하지 않고 원장만 갱신했다. 위 항목은 실행 재현이 아닌 코드 경로로 확인했다.

## 상태와 범위

상태는 `미점검 / 지적됨 / 수정됨 / 재검토 통과 / 정책 보류`를 사용한다. 아래 표는 초기 검토 기록이며 최신 수정 상태는 위 표를 따른다.
파일 경로는 별도 표기가 없으면 `backend/src/main/java/com/eeum/eeum/` 기준이다.

| 검토 단위 | 상태 | 확인한 경로와 남은 사항 |
| --- | --- | --- |
| 일반 가입·로그인·이메일 인증 | 지적됨 | AuthController → AuthService/EmailService → Account/Redis. 이메일별 실패 키 정규화는 있음. 외부 I/O 트랜잭션 점유는 m-02 |
| Kakao 로그인·가입·재인증 | 지적됨 | OAuthService, 임시 가입 토큰, provider ID 조회. 앱 식별 검증 누락 M-08, 이메일 신뢰 m-01 |
| 재발급·로그아웃 | 지적됨 | JwtProvider, TokenService, AuthService, RedisLockService, JWT 필터. M-02~04 |
| 비밀번호 변경·재설정 | 지적됨 | 일회용 토큰 compare-and-delete, 토큰 세대 검사와 BEFORE_COMMIT 회수 확인. 지연 정리 경쟁 M-04 |
| 사장 가입·정보 변경·심사·승인 | 지적됨 | OwnerInfo, OwnerApprovalService, AccountService, AdminAccountService. 관리자 권한·대상 계정 잠금 존재. 사업자 검증 M-01 |
| 제재·탈퇴·익명화 | 재검토 통과 | 본인 탈퇴의 미완료 거래 409 차단, 관리자 강제 탈퇴 감사 이력, 미지급 정산계좌 제한 보존·완료 후 파기를 재검토 |
| 계정 상태와 채팅·실시간 인증 | 재검토 통과 | ChatAccessHelper, ChatMessageService, STOMP 인증, 세션 회수·대조 경로 확인. DB 상태/세대 재검사와 송신자 잠금 존재 |
| 계정 상태와 알림 | 지적됨 | NotificationService → NotificationPushEventListener → FcmPushAdapter. 대기 푸시 M-06 |
| 계정 상태와 주문·정산 | 재검토 통과 | 주문 생성·환불 경로, 탈퇴 처리, 주간 정산, 미지급 정산계좌 보존과 완료 후 파기 재검토 |
| 실제 장애·경합·외부 계약 실행 검증 | 미점검 | Redis 데이터 유실, 동시 로그아웃/재발급, 지연 이벤트, 외부 OAuth/사업자 응답, MySQL 경합은 미실행 |
| 운영 데이터 전체 개인정보 보존 조사 | 미점검 | 모든 스냅샷·첨부파일·로그·백업의 실데이터 조사와 법적 보존기간 확정은 이번 코드 검토로 완료하지 않음 |

주요 상태: Account `ACTIVE/PENDING/WITHDRAWN/SUSPENDED`, 사장 승인 `PENDING/APPROVED/REJECTED`와 `reviewRequestedAt`, JWT 용도 `ACCESS/REFRESH/REAUTH/PASSWORD_RESET`, DB `tokenVersion`.
외부 의존성: MySQL, Redis, Kakao/Naver, 국세청 사업자 조회 API, SMTP, FCM, 파일 저장소, 실시간 Redis 중계.
스케줄러: AccountCleanupScheduler, WebSocketSessionReconciliationScheduler. DB 토큰 세대 컬럼은 `db/migration/common/V1__baseline.sql`에 존재한다. 운영 DB에 실제 적용됐는지는 확인하지 않았다.

## 확정 지적

### M-01 · Major · 지적됨 — 사업자 검증을 호출하지 않아도 검증 완료로 판단

- 위치: `application/auth/service/AuthService.java:143`, `domain/account/entity/OwnerInfo.java:100`, `application/account/service/OwnerApprovalService.java`의 `validateChecklistCompleted`.
- 사장 가입의 국세청 검증 호출은 주석 처리돼 있다. `isBusinessVerified()`는 사업자번호가 비어 있지 않고 개업일이 있는지만 확인한다. 공개 검증 API의 결과는 가입·심사와 결합되지 않는다.
- 따라서 검증 API를 건너뛰고 형식상 값을 제출하면 체크리스트에서 사업자 검증 완료로 표시되고 심사 요청이 가능하다. 관리자 최종 승인은 필요하므로 관리자 권한 자체의 무인 우회라고 단정하지 않는다.
- 수정 기준: 검증 결과를 실제 번호·대표자명·개업일과 결합하고 서버에서 검증해야 한다. 검증 대상 정보 변경 시 기존 증빙을 무효화한다.
- 회귀: 외부 검증 실패/미호출/타 사업자 증빙/번호 변경 후 심사 요청 거절, 정상 증빙 승인.

### M-02 · Major · 지적됨 — 로그아웃 회수가 Redis 데이터에만 의존

- 위치: `application/auth/service/AuthService.java:376`, `application/auth/service/TokenService.java:209`, `security/jwt/JwtAuthenticationFilter.java`.
- 로그아웃은 제시된 access token의 Redis 블랙리스트 등록과 refresh 키 삭제만 수행한다. 계정 tokenVersion은 바뀌지 않는다.
- 성공한 로그아웃 이후 Redis 데이터가 유실되면 미만료 access token은 블랙리스트 조회를 통과하고 DB 상태·세대도 일치해 재사용된다. Redis 접속 오류 때 인증 실패하는 동작과 Redis가 정상 응답하되 데이터가 비어 있는 상황은 다르다.
- 재발급과 경합할 때도 logout의 refresh 검증은 락 밖이다. 검증 뒤 다른 요청이 재발급을 완료하면 logout은 새 refresh를 삭제하지만 새 access까지 회수하지 않는다.
- 수정 기준: 로그아웃 범위에 맞는 영속 세대/세션 회수 및 발급과의 일관성 확보. 계정 전체 로그아웃인지 단일 세션 로그아웃인지는 API 동작을 명시한다.
- 회귀: 로그아웃 → Redis 키 유실 → 이전 access 거절, 재발급과 로그아웃의 두 실행 순서.

### M-03 · Major · 지적됨 — 같은 초에 발급한 JWT가 동일해질 수 있음

- 위치: `security/jwt/JwtProvider.java:81`.
- 동일 계정·세대·용도 토큰에는 랜덤 식별자가 없고 시간 외 claim이 같다. 사용 중인 JJWT 0.12.6은 Date를 초 단위로 직렬화한다. 같은 초에 발급하면 서명 입력이 같아 동일한 JWT가 된다.
- 재발급 후 이전 refresh가 그대로 유효해질 수 있고, 로그아웃 직후 같은 초에 로그인하면 새 access가 기존 블랙리스트 값과 같아 거절될 수 있다. 재인증·재설정 토큰도 같은 생성기를 사용한다.
- 수정 기준: 발급마다 고유한 `jti` 등 식별자 추가. 토큰 세대는 별도로 유지한다.
- 회귀: 고정된 초 안에서 연속 발급한 모든 용도 토큰의 비동일성, 이전 refresh 거절, 로그아웃 후 재로그인.
- 외부 근거: [JJWT 0.12.6 JwtDateConverter](https://raw.githubusercontent.com/jwtk/jjwt/0.12.6/impl/src/main/java/io/jsonwebtoken/impl/lang/JwtDateConverter.java)의 `date.getTime() / 1000L`.

### M-04 · Major · 지적됨 — 이전 이벤트가 새 세대 토큰을 삭제

- 위치: `application/auth/listener/AccountTokenCleanupEventListener.java:48`.
- 비밀번호 변경 등의 커밋 후 비동기 정리는 account ID 키를 조건 없이 삭제한다. 세대가 증가한 뒤 새 로그인이 refresh를 저장하고, 이전 이벤트가 늦게 실행되면 새 토큰이 삭제된다. allTokens 이벤트는 새 재인증·재설정 토큰도 같은 방식으로 지운다.
- DB 세대 검사는 낡은 토큰의 사용을 막지만 새 토큰 삭제를 막지는 못한다.
- 수정 기준: 이벤트 대상 세대/토큰에 한정한 원자적 조건부 삭제 또는 세대별 키. 락만 추가해도 새 로그인 후 늦게 실행되는 순서 자체는 해결되지 않는다.
- 회귀: 이벤트 실행을 막은 상태에서 새 로그인/재인증 → 이전 이벤트 실행 → 새 토큰 유지.
- 기존 `AccountTokenCleanupLockIntegrationTest`는 락을 기다리지 않고 삭제하는지만 검사하며 이 순서를 검증하지 않는다.

### M-05 · Major · 지적됨 — 탈퇴·제재 후 진행 중 요청이 새 주문을 생성할 수 있음

- 위치: `application/order/service/OrderService.java:81`, `application/account/service/AccountRegionService.java:53`.
- 주문 생성은 계정을 일반 조회하고 현재 쓰기 가능 상태를 확인하지 않는다. JWT 필터 통과 후 탈퇴/제재가 커밋되고 나서 주문 서비스가 진행되면 비활성 계정으로 주문 생성·재고 감소가 가능하다. 주문 생성이 계정 자체를 갱신하지 않아 Account의 @Version도 이를 차단하지 않는다.
- 활동지역 추가도 계정 상태 재검사/잠금 없이 자식 행을 추가한다. 탈퇴와 겹친 쓰기를 막는 규약이 일관되지 않다. 30일 동안 요청이 유지돼 익명화를 뚫는다는 가정은 이 지적의 근거로 사용하지 않았다.
- 수정 기준: 계정 상태 전이와 신규 거래 생성의 잠금/원자적 상태 조건을 통일한다. 관련 상점·상품 락과 교착 없는 순서도 함께 검토한다.
- 회귀: 필터 통과 후 멈춘 요청에 탈퇴/제재를 먼저 커밋 → 주문·재고·지역 쓰기 미발생.

### M-06 · Major · 지적됨 — 탈퇴·토큰 교체 후에도 큐에 남은 FCM 발송

- 위치: `application/notification/service/NotificationService.java:314`, `application/notification/listener/NotificationPushEventListener.java:28`.
- 이벤트가 FCM 토큰과 본문을 미리 보관한다. 비동기 리스너는 현재 계정 상태나 토큰 일치를 확인하지 않고 그대로 발송한다. FcmPushAdapter에도 발송 전 계정 재검증은 없다.
- 이벤트 대기 중 탈퇴해 DB 토큰이 지워지거나 기기 토큰이 바뀌어도 이전 기기로 개인정보를 포함할 수 있는 알림이 나간다. 탈퇴 시 토큰 삭제만으로는 막히지 않는다.
- 수정 기준: 발송 직전 현재 수신 자격 및 토큰 소유 관계 확인. 이미 외부 사업자에게 전달한 메시지는 회수 보장 범위에서 구분한다.
- 회귀: 이벤트 적재 → 탈퇴/토큰 교체 → 큐 실행 시 이전 기기 미발송.

### M-07 · Major · 지적됨 — 미지급 정산의 지급계좌도 30일 뒤 무조건 삭제

- 위치: `application/account/service/AccountCleanupService.java:93`, `application/store/service/SettlementAccountDeleteService.java:26`.
- 익명화 조건은 탈퇴 후 30일뿐이고 정산 상태를 확인하지 않는다. 정산계좌는 무조건 삭제된다. WeeklySettlement는 지급 금액·증빙 참조를 보관하지만 은행·계좌번호·예금주 스냅샷을 갖고 있지 않다.
- 미지급/실패/격리 정산이 30일을 넘기면 정산 채무 행은 남는데 서비스가 보관하던 지급계좌는 사라진다. 탈퇴 계정은 스스로 다시 등록할 수도 없다. 이미 정산 완료된 계좌를 계속 보관하자는 지적은 아니다.
- 수정 기준: 미완료 정산에 필요한 정보의 최소 보존 또는 본인확인 기반 지급계좌 재수집 경로를 정의하고 파기와 연결한다. 보존기간은 P-02에서 결정한다.
- 회귀: 미지급/실패/격리 상태에서 익명화 후에도 승인된 지급 복구 경로 존재, 완료 후 보존기한 도달 시 파기.

### M-08 · Major · 지적됨 — Kakao 토큰의 발급 앱을 확인하지 않음

- 위치: `application/auth/service/OAuthService.java:46`.
- 입력 토큰으로 `/v2/user/me`만 호출하고 서비스에 허용된 Kakao 앱의 토큰인지 검증하지 않는다. 로그인과 OAuth 재인증이 이 경로를 공유한다.
- 코드상 타 앱 발급 토큰을 거절할 조건이 없으므로 서비스의 OAuth 앱 경계가 검증되지 않는다. 이것만으로 기존 특정 계정 탈취가 가능하다고 단정하지는 않는다.
- 수정 기준: 허용 앱 ID 설정과 토큰 정보의 `app_id` 대조, 사용자 ID·유효기간 검증 후 프로필 사용. 또는 서버가 앱에 결합된 인가 코드 교환 경로를 맡는다.
- 회귀: 유효하더라도 다른 앱 토큰은 로그인·가입·재인증에서 거절. 실제 외부 앱 토큰 재현은 미실행.
- 외부 근거: [Kakao REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api#access-token-info)는 토큰 정보 조회 응답에 `app_id`를 제공한다.

### m-01 · Minor · 지적됨 — Kakao 이메일의 인증 상태를 버림

- 위치: `application/auth/service/OAuthService.java`, `domain/account/entity/Account.java:151`.
- `is_email_valid`/`is_email_verified`를 읽지 않고 email만 전달하며 OAuth 계정은 `emailVerified=true`로 생성한다. 미인증·무효 이메일을 인증된 것으로 저장할 수 있다.
- 기존 계정에 이메일만으로 자동 연결하는 경로는 확인되지 않아 계정 탈취로 확대 평가하지 않았다. 허위 인증 상태 및 이메일 유니크 충돌은 남는다.
- 수정 기준: 이메일 신뢰 상태를 전달하고 두 플래그가 확인되지 않으면 인증 완료로 표시하지 않는다.
- 근거: [Kakao 사용자 정보 응답](https://developers.kakao.com/docs/ko/kakaologin/rest-api#req-user-info)은 email 값과 유효성·인증 여부를 구분한다.

### m-02 · Minor · 지적됨 — 인증 경로의 외부 I/O가 DB 트랜잭션 안에 남음

- 위치: `AuthService.sendEmailVerificationCode`, `sendPasswordResetEmail`, `reAuth`; `AdminAccountService.approveOwner`.
- DB 조회 뒤 SMTP 발송/외부 OAuth 확인을 동기 수행하고, 관리자 승인은 계정·사업자 행 잠금 뒤 위치 API를 호출할 수 있다. 외부 지연 동안 커넥션·행 잠금 점유가 연장된다.
- 수정 기준: 외부 검증을 트랜잭션 밖으로 분리하고 결과 반영 시 상태·세대·증빙을 재검증한다. 실제 풀 고갈은 부하 테스트 전이므로 확정 장애로 기술하지 않는다.

## 정책 보류

| ID | 항목 | 확인된 상태와 결정 필요 사항 |
| --- | --- | --- |
| P-01 | 미완료 주문·예약이 있는 계정의 탈퇴/제재 | 탈퇴 처리에 일반 주문·예약의 진행 상태 사전조건이나 담당자 인계가 없다. 사장 계정은 접근 차단되고 상점은 닫힌다. 미완료 거래를 먼저 정리할지, 탈퇴를 허용하며 CS에 인계할지 결정해야 한다. 고객 환불 API의 존재만으로 전체 이행 책임이 해결됐다고 보지 않는다. |
| P-02 | 개인정보 삭제·보존 범위 | Account 식별정보·프로필·활동지역·OwnerInfo·정산계좌를 파기하지만 주문/채팅/알림 등 활동 기록은 남긴다. 본문·스냅샷·첨부파일에 남은 식별정보, 미지급 채무의 최소 보존, 로그·백업 삭제 범위와 접근 주체를 확정해야 한다. 법적 보존기간 판정은 수행하지 않았다. |

## 기존 방어와 테스트 근거

- JWT 필터는 DB 계정 상태·현재 권한·tokenVersion을 확인한다. 제재/비밀번호 변경/탈퇴 이벤트의 BEFORE_COMMIT 세대 증가는 Redis 정리 실패 시에도 이전 세대를 차단한다. 이 방어를 M-02 로그아웃에도 적용된 것으로 오인하면 안 된다.
- 재인증/비밀번호 재설정 JWT의 Redis 소비는 compare-and-delete로 원자화돼 있다. 이메일별 시도 제한 키에는 소문자/공백 정규화가 있어 단순 대소문자 우회를 지적으로 채택하지 않았다.
- 관리자 경로는 `/admin/**` 역할 제한, 사장 경로는 `/owner/**`와 승인 전 입력 경로 예외로 구분한다. 승인/제재 대상 계정 잠금과 본인 소유 지역·알림 조회 조건을 확인했다.
- 채팅 송신자 쓰기 가능 상태 확인과 계정 잠금, WebSocket 토큰 만료·DB 상태/세대 대조, SSE 상태/세대 대조를 확인했다. 연결을 실제로 유지하며 장애를 주는 검증은 미실행이다.
- 기존 테스트: `JwtAuthenticationFilterIntegrationTest`, `AdminAccountStatusLockIntegrationTest`, `AccountOptimisticLockIntegrationTest`, `AccountTokenCleanupAfterCommitIntegrationTest`, `AccountTokenCleanupLockIntegrationTest`, `OneTimeTokenConsumptionIntegrationTest`, `AccountWithdrawalConcurrencyIntegrationTest`, `AccountCleanupServiceTest`, `AccountCleanupFavoriteIntegrationTest`, `AdminAccountServiceOwnerReviewGuardTest`, `StompAuthChannelInterceptorTest` 등. 파일/테스트 소스를 대조했으며 성공했다고 주장하지 않는다.

## 다음 반복의 종료 조건

1. 확정 지적을 토큰/사업자 검증/계정 상태 경합/탈퇴 후 데이터·발송으로 묶어 수정한다.
2. 각 항목의 위 회귀 순서를 테스트로 고정하고 사용자가 실행한 결과를 확인한다. 동시성은 실제 MySQL/Redis 기반으로 확인한다.
3. P-01/P-02의 정책을 코드·운영 인계 경로와 연결한다.
4. 수정 diff를 다시 검토하고, 미점검 실행 검증 및 데이터 보존 조사 범위를 별도로 남긴다. Major가 남아 있는 상태에서 3단계 통과로 표시하지 않는다.
