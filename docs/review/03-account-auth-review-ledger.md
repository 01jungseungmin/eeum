# 3단계 계정·인증·권한 검토 원장

## 2026-10-07 최종 재검토·실행 검증 — 최신 판정

**판정: 3단계 재검토 통과. 이번 단계 범위에서 Critical/Major는 남지 않았다.**
FCM 토큰 단일 소유권·계정 상태 경합·UNIQUE 예외 응답의 수정 범위를 다시 검토했고, 계정·인증 단위 회귀와 Docker 기반 MySQL/Redis 통합·동시성 테스트 결과를 확인했다.

| 검토 단위 | 상태 | 검증 근거 |
| --- | --- | --- |
| F3-01 심사 부속정보와 승인·탈퇴 직렬화 | 재검토 통과 | account → owner_info 잠금과 최신 상태 재검사, 관련 단위·통합 회귀 통과. |
| F3-02 정지 판매자의 신규 주문·예약 | 재검토 통과 | 구매자 → Store → 판매자 Account 잠금 및 ACTIVE 검사, 계정 상태 잠금 통합 회귀 통과. |
| F3-03 FCM 토큰 단일 소유권·상태 전이 | 재검토 통과 | V30의 중복 정리·UNIQUE 제약, 등록 공용 Redis 락, 잠금 후 ACTIVE 재검사 및 실제 토큰 교환 경쟁 통합 테스트 통과. |
| F3-04 이메일·재설정 코드 원자 소비 | 재검토 통과 | Lua compare-and-delete와 일회용 토큰 MySQL/Redis 통합 회귀 통과. |
| F3-05 공개 API 설명 | 재검토 통과 | 익명화·전체 세션 로그아웃·OAuth access token 계약과 Swagger 설명 일치. |
| 사업자번호·OAuth UNIQUE 경합 응답 | 재검토 통과 | 서비스 내부의 rollback-only 예외 변환을 제거하고 제약명 기반 전역 409 매핑 회귀 테스트 통과. |

### 실행 결과

- 컴파일: 2026-10-07 사용자 실행 `gradlew compileJava compileTestJava` 성공.
- 계정·인증 단위 회귀: 사용자 실행 대상 범위 성공. 최초 Mockito `saveAndFlush()` stale stubbing 1건은 현재 `save()` 구현에 맞춰 제거했고, `AuthServiceTokenCleanupTest`와 `GlobalExceptionHandlerTest`를 재실행해 성공.
- MySQL/Redis 통합·동시성: `AccountOptimisticLockIntegrationTest`, `AccountStageThreeRegressionIntegrationTest`, `AdminAccountStatusLockIntegrationTest`, 탈퇴·토큰 정리·일회용 토큰·Redis lock·FCM 이전 테스트가 Docker 환경에서 성공.
- FCM 토큰 교환 경쟁: `FcmTokenTransferIntegrationTest`가 2026-10-07 Docker 환경에서 성공. 두 계정의 동시 토큰 교환은 15초 안에 끝나며, 실패 시 재시도 가능한 락 획득 실패만 허용하고 토큰별 소유 계정 수가 최대 1명임을 검증한다.

### 다음 단계로 이관할 관찰 항목

- 테스트 종료 시 `taskScheduler` 종료 대기와 Hibernate create-drop의 테이블 삭제 경고가 있었으나 Gradle은 성공했고 테스트 실패는 없었다. 이는 3단계 결함으로 분류하지 않으며, 5단계 실시간·알림/자원 검증 및 9단계 운영 준비도에서 스케줄러 종료·테스트 데이터 정리 관점으로 확인한다.

## 2026-10-03 수정 범위 재검토·실행 검증 — 최신 판정

**판정: 3단계 미완료. Critical은 확인하지 못했으나 Major 3건이 남아 다음 단계로 진행할 수 없다.**
이번 수정 재검토 범위는 F3-01~F3-05, M-01~M-08, P-01~P-02를 고친 커밋과 현재 미커밋 `AccountRepository`·낙관적 락 통합 테스트 변경이다. 범위 밖 기존 문제는 차단 항목으로 확대하지 않았다.

| 검토 단위 | 상태 | 최신 결과 |
| --- | --- | --- |
| F3-01 심사 부속정보와 승인·탈퇴 직렬화 | 재검토 통과 | 모든 심사 입력 쓰기가 account → owner_info 비관적 잠금과 ACTIVE/PENDING 최신 상태 재검사를 공유한다. |
| F3-02 정지 판매자의 신규 주문·예약 | 재검토 통과 | 주문·예약이 구매자 → Store → 판매자 Account 순으로 잠근 뒤 판매자 ACTIVE를 확인한다. |
| F3-03 FCM 토큰 단일 소유권·상태 전이 | 지적됨 | 아래 R3-03A/R3-03B가 남아 있다. |
| F3-04 이메일·재설정 코드 원자 소비 | 재검토 통과 | Redis Lua compare-and-delete가 값 비교와 삭제를 한 연산으로 수행한다. 실제 Redis 경쟁 실행은 미점검이다. |
| F3-05 공개 API 설명 | 재검토 통과 | 탈퇴 익명화·전체 세션 로그아웃·OAuth access token 계약과 Swagger 설명이 일치한다. |
| 사업자번호·OAuth UNIQUE 경합의 예외 응답 | 지적됨 | 아래 R3-04가 이전 범위에서 새로 확인됐다. |
| 계정·인증 단위 회귀 | 재검토 통과 | 관련 단위 테스트 25개 클래스를 실행해 `BUILD SUCCESSFUL`을 확인했다(1분 22초). |
| MySQL/Redis 동시성 실행 검증 | 미점검 | Docker를 사용할 수 없어 Testcontainers 기반 9개 클래스, 37개 테스트가 전부 skip됐다. 통과로 집계하지 않는다. |

### R3-03A · Major · 지적됨 · 잔존 — FCM 토큰의 DB 단일 소유권이 보장되지 않음

- 위치: `domain/account/repository/AccountRepository.java:106-117`, 운영 DDL의 `account.fcm_token`.
- `transferFcmToken()`은 대상 계정 또는 기존 소유자를 한 UPDATE로 변경하지만 `fcm_token` UNIQUE 제약과 Flyway 백업이 없다. 같은 토큰을 두 계정이 동시에 등록하는 경우, 행 잠금 대기 순서에 따라 두 계정에 토큰이 남을 수 있다.
- 영향: 하나의 기기로 서로 다른 계정의 알림 본문이 전송될 수 있다.
- 종료 조건: 기존 중복 토큰 정리 절차를 포함한 UNIQUE DDL, 충돌/재시도 규약, 실제 MySQL barrier·lock-wait 경쟁에서 토큰 보유 행 수가 항상 1 이하임을 검증하는 통합 테스트.

### R3-03B · Major · 지적됨 · 잔존 — 정지·탈퇴 직후 FCM 토큰이 다시 저장될 수 있음

- 위치: `application/account/service/AccountService.java:180-188`, `domain/account/repository/AccountRepository.java:111-116`.
- FCM 등록 요청이 비잠금 상태 조회에서 ACTIVE를 확인한 뒤, 관리자 정지 또는 탈퇴가 토큰을 지우고 커밋하면 늦게 실행된 bulk UPDATE가 SUSPENDED/WITHDRAWN 계정에 토큰을 다시 쓸 수 있다. UPDATE에 상태 조건과 영향 행수 처리도 없다.
- 영향: 탈퇴 취소 뒤 ACTIVE로 복귀하면 보관돼서는 안 되는 기기 토큰이 다시 유효해질 수 있다.
- 종료 조건: 동일 Account의 잠금 뒤 ACTIVE 재검사 또는 상태 조건의 원자 UPDATE와 0행 처리 규약, 정지/탈퇴와 토큰 등록의 실제 MySQL 경쟁 테스트.

### R3-04 · Major · 지적됨 · 이전 누락 — UNIQUE 경합이 의도한 409가 아닌 500으로 끝날 수 있음

- 위치: `application/account/service/AccountService.java:251-255`, `application/auth/service/AuthService.java:311-316`.
- 트랜잭션 안에서 `flush()`/`saveAndFlush()`의 `DataIntegrityViolationException`을 비즈니스 예외로 바꾸면 JPA 트랜잭션은 이미 rollback-only일 수 있다. 프록시 종료 시 `UnexpectedRollbackException`이 되어 사업자번호 동시 변경 또는 OAuth 가입 완료 경쟁이 의도한 409 대신 500으로 관찰될 수 있다.
- 종료 조건: 제약 위반 변환을 안전한 별도 경계 또는 제약명 기반 전역 예외 매핑으로 옮기고, 실제 MySQL 두 경쟁 요청에서 패배 요청이 500이 아니라 각 `ACCOUNT_*` 409으로 응답함을 검증.

### 실행 기록

- 단위: `./gradlew --no-daemon --console=plain test`로 계정 상태·탈퇴·심사·사업자 검증·토큰 정리·로그아웃·OAuth·이메일 코드·JWT/WebSocket 관련 25개 테스트 클래스를 실행했고 성공했다. 단위 테스트는 Redis·Repository를 mock하므로 동시성 안전성을 증명하지 않는다.
- 통합/동시성: `AccountOptimisticLockIntegrationTest`, `AccountStageThreeRegressionIntegrationTest`, `AdminAccountStatusLockIntegrationTest`, 계정 탈퇴·토큰 정리·일회용 토큰·FCM 이전·Redis lock 통합 테스트 9개 클래스를 요청했으나 Docker unavailable로 37개 전부 skip됐다.
- 형식: 현재 변경 범위의 `git diff --check`를 통과했다.

## 2026-09-30 수정 반영 상태 — 최신 판정

사용자가 승인한 F3-01~F3-05 수정이 반영됐다. 애플리케이션 코드 기준으로는 모두 `수정됨`이며, 아래의 이전 정적 검토 기록은 발견 당시의 근거를 보존한다.

| ID | 상태 | 반영 내용 |
| --- | --- | --- |
| F3-01 | 수정됨 | 심사 부속정보의 모든 쓰기가 account → owner_info 잠금과 활성 상태 재검사를 거친다. 승인 뒤 계좌·영업시간·상점 정보·대표 메뉴 변경을 막는다. |
| F3-02 | 수정됨 | 주문과 방문 예약 생성에서 OPEN 상점뿐 아니라 판매자 Account도 잠가 ACTIVE를 확인한다. 정지 판매자의 기존 장바구니·알려진 상점 ID 거래를 거절한다. |
| F3-03 | 수정됨 | FCM 토큰 등록은 이전 소유 계정의 토큰을 같은 DB UPDATE로 해제하고, 로그아웃은 해당 계정의 기기 토큰도 해제한다. 일괄 이전도 version을 올려 stale Account 덮어쓰기를 차단한다. |
| F3-04 | 수정됨 | 이메일 인증·비밀번호 재설정 코드를 compare-and-delete로 소비한다. |
| F3-05 | 수정됨 | 탈퇴 익명화, 전체 세션 로그아웃, OAuth access token 입력에 맞게 Swagger 설명을 정정했다. |

회귀 범위는 심사 입력 가드, 정지 판매자의 주문·예약 거절, FCM 소유 이전·로그아웃·stale write, 이메일 코드 재소비를 포함한다. 사용자가 실행하는 Gradle 및 MySQL/Redis 동시성 검증 결과는 아직 이 원장에 반영하지 않았다.

---

## 전체 최종 정적 재검토 — 이전 발견 기록

사용자의 명시적 전체 검토 요청에 따라 기존 두 지적의 수정 범위를 넘어 3단계 진입점과 직접 연계를 다시 검토했다. 아래 판정이 이전 부분 재검토의 통과 문구보다 우선한다.

**판정: 3단계 미완료. 새로 확인한 Major 3건, Minor 2건. Critical은 확인하지 못했다.** 모두 코드 경로에 근거한 정적 지적이며 실행 재현 결과가 아니다. Gradle·DB/Redis 테스트·외부 API 실호출·배포는 수행하지 않았다. 애플리케이션 코드는 수정하지 않았다.

| 전체 검토 단위 | 확인한 진입점·직접 의존 | 판정 및 근거 |
| --- | --- | --- |
| 일반 가입·로그인 | AuthController, AuthService.signup/login, EmailService, 요청 DTO, Account UNIQUE, RateLimitKeys | 이메일-가입토큰 결합·비밀번호 해시·역할 서버 지정 확인. 인증코드 소비 F3-04 |
| Kakao OAuth·가입 완료 | OAuthService, oauthLogin/oauthComplete/reAuth, OAuth DTO, provider UNIQUE | 앱 ID·회원번호·만료·이메일 신뢰 플래그 검증과 임시 토큰의 서버 저장·provider 중복 제약 확인 |
| JWT·재발급·로그아웃·Redis 유실 | JwtProvider, JwtAuthenticationFilter, SecurityConfig, TokenService, AccountLogoutService, token invalidation/cleanup listeners | 토큰 용도·세대·DB 권한 대조, 재발급 락, BEFORE_COMMIT 세대 회수, 지연 compare-and-delete 확인. 기기 푸시 연결 회수 F3-03 |
| 비밀번호 변경·재설정·재인증 | AccountService.changePassword, AuthService reset/reAuth 경로, TokenService, EmailService | 토큰 소유자·용도·세대와 원자 소비, 상태 검사 및 Account @Version 확인. JWT 이전 단계인 이메일 코드의 원자성 F3-04 |
| 본인 탈퇴·관리자 강제 탈퇴·익명화 | AccountService.withdraw, AdminAccountService.forceDeleteAccount, AccountWithdrawalProcessor/Guard, WithdrawalObligationRepository, AccountCleanupService/Scheduler | 본인 탈퇴 READ_COMMITTED 및 거래 가드, 관리자 감사 이력, 30일 익명화와 지급계좌 후속 파기 확인. 주문·정산 행 보존. API 설명 F3-05 |
| 사장 정보·심사·권한 승격 | AccountService.updateOwnerInfo, OwnerApprovalService 전체 공개 메서드, AdminAccountService approve/reject, OwnerInfo, OwnerApprovalAccessChecker | 외부 사업자 검증과 증빙 결합, 승인/거절/사업자번호 변경 잠금 확인. 심사 부속정보 쓰기 F3-01 |
| 관리자 접근·상태 전이 | AdminAccountController, SecurityConfig, AccountSanctionPolicy, AdminAccountService | 관리자 라우트·메서드 권한, 관리자 대상 제재 금지, 정지/해제/탈퇴/익명화 후 복원 가드 확인. 정지된 판매자의 신규 거래 F3-02 |
| 활동지역·자기 정보·소유권 | AccountController, AccountRegionController/Service/VerificationService, DTO·mapper | 요청자 ID를 인증정보에서 취득, 지역 소유권 쿼리, 쓰기 Account 잠금, GPS 외부 I/O 분리, 자기/관리자 응답 범위 확인 |
| 주문·예약·정산 연계 | OrderService.createOrder, VisitReservationService 생성·상점 검사, PaymentService 소유권 조회, SettlementQueryService, ManualSettlementPayoutService, AdminStoreService | 구매자 상태/잠금·조회 소유권과 관리자 지급 복구 접근 확인. 판매자 상태 누락 F3-02. R3-01/R3-02 수정 유지 |
| 채팅·SSE·WebSocket 연계 | ChatAccessHelper, ChatMessageService, 채팅 응답 DTO, StompAuthChannelInterceptor, AccountWriteGuard, SessionTerminationListener, RealtimeRelaySubscriber, WebSocketSessionReconciliationScheduler, NotificationSseController | 채팅 참여자·메시지 소유권, 송신자 잠금, 연결 세대 대조, 인스턴스별 회수 보정과 새 세대 보존 확인. 비동기 회수 지연과 실행 검증은 별도 |
| 알림·개인정보 연계 | Account.updateFcmToken/withdraw/anonymize, NotificationService/PushEventListener, PushEligibilityReader, AccountRepository | 탈퇴·토큰 교체 뒤 큐 발송 재검사 확인. 계정 간 동일 FCM 토큰 연결 F3-03 |
| DDL·회귀 테스트 | Account/OwnerInfo/SettlementAccount, AccountRepository/OwnerInfoRepository, V28/V29, account/auth 테스트와 WithdrawalObligationIntegrationTest, AccountStageThreeRegressionIntegrationTest | 스키마와 증빙/감사 enum 연결, 기존 테스트 범위 확인. 신규 지적에 대한 필요한 회귀는 아래 명시. 실제 마이그레이션·테스트 성공은 미확인 |

### F3-01 · Major · 지적됨 · 이전 누락 — 심사 부속정보 저장이 승인·탈퇴와 직렬화되지 않음

- 위치: `OwnerApprovalService.saveSettlementAccount`(174행), `updateBusinessHours`, `updateStoreBusinessInfo`, `saveRepresentativeMenu`, `validateReviewEditable`.
- 진입점: `/owner/stores/me/settlement-account` 등 승인 전 입력 API. OwnerApprovalAccessChecker는 OwnerInfo 존재만 확인한다.
- 선행 상태/순서: ACTIVE/PENDING 신청자가 계좌 저장 요청을 시작하여 PENDING 검사를 통과 → 관리자가 account/owner_info 잠금으로 승인 커밋 → 먼저 시작한 저장 요청이 기존 SettlementAccount를 UPDATE하여 커밋. 이미 APPROVED여서 거절해야 할 계좌 변경이 승인 뒤 반영된다. 필터 통과 후 계정이 정지/탈퇴한 경우도 같은 쓰기 경로에서 현재 계정 상태를 다시 확인하지 않는다.
- 기존 방어 실패: 부속정보 저장은 Account/OwnerInfo를 잠그지 않고 Account도 갱신하지 않는다. 따라서 Account의 @Version 및 관리자 쪽 잠금만으로 해당 UPDATE가 실패하지 않는다. SettlementAccount에도 @Version은 없다.
- 수정 방향: 모든 심사 관련 쓰기의 시작에서 같은 account → owner_info 잠금과 상태 재검사를 적용하고 필요한 자식 행까지 일관된 순서로 보호한다.
- 필요한 회귀: 계좌 변경과 승인/정지/강제 탈퇴의 양방향 실행 순서. 승인이 먼저 완료되면 저장을 거절하고, 저장이 먼저면 승인이 최신 값을 관찰해야 한다.

### F3-02 · Major · 지적됨 · 이전 누락 — 정지된 사장에게 신규 주문·예약 생성 가능

- 위치: `AdminAccountService.suspendAccount`(102행), `OrderService.createOrder`, `VisitReservationService.validateReservableStore`(595행).
- 선행 상태/순서: 승인된 사장의 OPEN 상점에 고객 장바구니가 존재 → 사장 계정 정지 커밋 → 고객이 POST /orders 또는 POST /reservations/visits/stores/{storeId} 요청.
- 기존 방어 실패: 계정 정지는 Account만 SUSPENDED로 바꾸며 상점은 OPEN이다. 주문·예약 생성은 고객 Account와 Store 상태만 검사하고 판매자 Account 상태를 확인하지 않는다. Store 목록의 ACTIVE 필터는 이미 알려진 상점 ID나 기존 장바구니를 사용하는 쓰기 요청에 적용되지 않는다.
- 영향: 사장은 로그인·처리를 못 하는 상태인데 고객의 새 거래·재고 차감·결제 대기가 생성된다.
- 수정 방향: 판매자 상태 전이와 신규 거래 허용 조건을 같은 동시성 규약으로 연결한다. 단순 목록 숨김만으로 해결하지 않는다.
- 필요한 회귀: 정지 후 기존 장바구니 주문/알려진 상점 ID 예약 거절, 정지와 생성 경합, 제재 해제 후 정상 경로.

### F3-03 · Major · 지적됨 · 이전 누락 — 동일 FCM 토큰의 계정 간 연결이 남음

- 위치: `AccountService.updateFcmToken`(180행), `AccountLogoutService.logout`, `PushEligibilityReader.canSend`, Account의 fcm_token 컬럼.
- 선행 상태/순서: 활성 계정 A가 FCM 토큰 T 등록 → A 로그아웃 → 계정 B가 같은 토큰 T 등록 → A에게 채팅/주문 알림 발생.
- 기존 방어 실패: 로그아웃은 JWT 세대만 회수하고 A의 FCM 토큰은 남긴다. B의 등록은 A의 연결을 해제하지 않으며 fcm_token 유일성 제약도 없다. A는 ACTIVE이고 저장값도 T이므로 PushEligibilityReader가 A의 알림 발송을 허용한다.
- 영향: B가 사용하는 기기로 A의 알림 본문이 전달될 수 있다. 임의의 제3자 기기 토큰을 추측할 수 있다는 가정은 필요하지 않으며, 같은 토큰을 등록하는 계정 전환만으로 성립한다.
- 수정 방향: 기기 토큰의 현재 계정 연결을 원자적으로 이전하고 로그아웃의 연결 해제 및 대기 이벤트 검사를 일치시킨다.
- 필요한 회귀: A→B 동일 토큰 이동 후 A 알림 미발송, 등록 경쟁에서 단일 소유, 지연 로그아웃/푸시가 B의 새 연결을 손상시키지 않음.

### F3-04 · Minor · 지적됨 · 이전 누락 — 이메일 인증코드 소비가 원자적이지 않음

- 위치: `EmailService.verifyCodeAndIssueToken`(67행), `verifyPasswordResetCode`(127행).
- 같은 코드를 동시에 GET한 요청 둘이 모두 일치 검사를 통과하고 DELETE 결과와 무관하게 성공한다. 재발송이 GET과 DELETE 사이에 새 코드를 저장하면 옛 검증 요청이 새 코드도 삭제할 수 있다.
- JWT 재인증/재설정 토큰의 compare-and-delete는 그 이전 단계인 이메일 코드에 적용되지 않는다. 이를 곧바로 비밀번호 탈취로 확대하지 않는다.
- 수정 방향/회귀: 이메일 코드도 값 일치와 소비를 한 연산으로 묶는다. 동일 코드 동시 검증은 한 번 성공, 재발송된 다른 코드는 이전 검증이 삭제하지 않아야 한다.

### F3-05 · Minor · 지적됨 · 이전 누락 — 공개 API 설명과 실제 동작 불일치

- `AccountController` 63행은 탈퇴 후 30일에 물리 삭제한다고 설명하지만 실제로는 익명화하고 계정 행·거래 이력을 보존한다.
- `AuthController` 114행은 로그아웃을 access 블랙리스트 등록으로 설명하지만 현재는 계정 tokenVersion 회수로 기존 세션 전체를 무효화한다. OAuth 로그인 설명도 인가 코드라고 적혀 있으나 요청 DTO는 accessToken이다.
- 수정 방향: Swagger 계약을 실제 익명화·계정 전체 로그아웃·OAuth access token 입력으로 정정한다.

### 확인 한계와 다음 판정 조건

- 기존 R3-01/R3-02는 해결 상태를 유지한다. 이번 5건은 사용자 지정 전체 범위에서 확인한 이전 누락이며 이전 두 수정의 회귀로 단정하지 않는다.
- Kakao 앱 식별·토큰 만료·이메일 신뢰 플래그는 [공식 REST 문서](https://developers.kakao.com/docs/ko/kakaologin/rest-api#access-token-info)와 대조했다. 외부 실호출은 하지 않았다.
- 런타임 경합, Redis 장애, 테스트 성공, 운영 DDL 적용 여부, 전체 로그·백업·첨부파일의 실제 개인정보 보존 상태는 정적 검사로 확인하지 않았다. 9단계 운영 준비도 전체 완료를 뜻하지 않는다.
- F3-01~03 수정 및 범위 재검토가 끝나야 코드 기준 완료 판정이 가능하다. 사용자 실행 테스트는 그 후에도 별도 확인한다.

---

아래는 이전 검토 이력이다. 최신 전체 판정은 위 내용을 따른다.

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
| M-07 | 재검토 통과 | R3-01 수정 확인. PAID 자체의 무조건 보존을 제거하고 미지급 원장·미완료 주간 정산·원장 누락·PENDING 결제·미완료 취소로 보존 여부 판정. 실행 검증 대기 |
| M-08 | 수정됨 | Kakao access_token_info의 앱 ID·사용자 ID·잔여 유효기간 검사 후 user/me 사용자 ID 대조. 로그인과 재인증 모두 적용 |
| m-01 | 수정됨 | Kakao 이메일 유효·인증 플래그를 확인하고 미확인 이메일은 저장 입력에서 제외. OAuth Account의 이메일 인증 상태도 실제 결과 반영 |
| m-02 | 수정됨 / 일부 지적 정정 | SMTP·재인증 OAuth 경로의 전체 트랜잭션 제거, 필요한 계정 조회만 AuthAccountReader의 read-only 트랜잭션 사용. 기존 관리자 위치 조회는 외부 API가 아니라 Region/Location DB 조회였으므로 이 부분 지적은 철회 |
| P-01 | 재검토 통과 | 본인 탈퇴는 미완료 주문·방문예약·중고 예약·정산·취소 작업이 있으면 409으로 차단. 관리자 강제 탈퇴는 해당 이력을 보존하고 `FORCE_WITHDRAW` 감사 이력을 남김 |
| P-02 | 재검토 통과 | 사용자 확정 정책 및 R3-01 수정 반영 확인. 30일 익명화 후 미지급 계좌를 보존하고 의무 해소 후 일일 스케줄러에서 파기. 전체 법적 보존기간·백업 정책은 별도 범위 |

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

사용자 수정 후 재검토에서 R3-01/R3-02 해소를 확인했다. **3단계 코드 정적 검토는 통과, 실행 검증은 대기**다. 이번 검토에서 추가로 확정한 Critical/Major는 없다. Gradle·MySQL/Redis 통합·동시성 테스트는 사용자 실행 대기이며 실행하지 않았다.

- R3-01: `requiresSettlementAccount()`의 별도 Payment 조건이 PENDING으로 한정됐고, 미지급 원장·주간 정산·원장 누락 및 미완료 취소 조건은 유지된다. `WithdrawalObligationIntegrationTest`에 PAID + SETTLED 보존 해제, ACCRUED 보존, PENDING 보존 사례가 추가됐다. 이 테스트가 실제 WeeklySettlement COMPLETED 및 계좌 삭제까지 실행하는 종단 테스트는 아니라는 한계는 남는다.
- R3-02: CART_EMPTY 판정이 상점 참조보다 앞으로 이동했다. `OrderServiceTest`에서 빈 항목 목록과 null 상점에 대해 CART_EMPTY 및 상점 잠금 미호출을 검증한다.
- 기존 탈퇴 가드·익명화 후속 파기, 로그아웃의 DB 세대 회수·지연 Redis 조건부 삭제, 사업자 검증 증빙 확인·예약 생성의 계정 잠금 연결을 다시 대조했다.

### 이전 최종 재검토 지적 기록 — R3-01/R3-02 현재 재검토 통과

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
