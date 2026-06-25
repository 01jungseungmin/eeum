# Account 도메인 SDD

> 코드 경로: auth, account, admin/account, ownerInfo, accountRegion, region, location, audit
> 비고: 회원, 인증, 활동지역, 사장승인, 감사로그

## AdminAuditLog 참고

AdminAuditLog는 기존 SDD에서 Account 도메인 근처에 배치되어 있으나, 실제 설계상 관리자 작업 전반에 적용되는 감사 로그 기능이다.

따라서 상세 구현 상태와 정책은 `common.md`의 "관리자 감사 로그 구현 상태"를 기준으로 한다.

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-1. 회원정보 관리

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자의 회원 정보를 관리하기 위한 기능으로, 회원가입, 로그인, 회원 정보 조회 및 수정, 회원 탈퇴 기능을 포함한다.

사용자는 서비스 이용을 위해 계정을 생성하며, 계정 정보는 Account 엔티티를 통해 관리된다. 사장 회원의 경우 추가적인 사업자 정보를 OwnerInfo 엔티티를 통해 관리하며, 일반 사용자와 구분되는 권한을 가진다.

또한 사용자는 활동 지역을 최대 2개까지 등록할 수 있으며, 해당 정보는 AccountRegion 엔티티를 통해 관리된다. 각 활동 지역은 Region 엔티티와 연관되며, 사용자의 실제 위치 기반 인증 여부를 verified 속성으로 관리한다.

지역 기반 서비스 제공을 위해 Region에는 대응되는 위치 정보가 존재하며, 이는 Location 엔티티에 위도(latitude)와 경도(longitude) 형태로 저장된다. 이를 통해 사용자의 현재 위치와 등록된 지역 간의 거리 비교가 가능하도록 설계하였다.

## 5.1 Account 도메인

### ● Entity

«Entity» Account
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
accountId | Long | PK, NOT NULL | 회원 ID
primaryRegionId | Long | nullable | 대표 활동 지역 (AccountRegion.accountRegionId 참조, FK 제약 없음 — 서비스 레이어에서 관리)
email | String | UNIQUE, NOT NULL | 로그인 이메일
password | String | nullable | 일반 로그인 시 필수, OAuth 로그인 시 null
name | String | NOT NULL | 실명
phone | String | NOT NULL | 전화번호
provider | OAuthProvider | NOT NULL | OAuth 로그인 시 필수, 일반 로그인 시 LOCAL (KAKAO / LOCAL)
providerId | String | nullable | LOCAL → providerId = null, OAuth → providerId NOT NULL
profileImageUrl | String | DEFAULT "/default-profile.png" | 사용자 프로필 이미지 URL
nickname | String | UNIQUE, nullable | 닉네임 (OAuth PENDING 상태에서는 임시값 허용)
role | AccountRole | NOT NULL | ROLE_USER / ROLE_OWNER / ROLE_ADMIN
status | AccountStatus | NOT NULL | PENDING / ACTIVE / SUSPENDED / WITHDRAWN
emailVerified | boolean | NOT NULL, DEFAULT false | 이메일 인증 여부
fcmToken | String | nullable | 푸시 발송용 디바이스 토큰
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속
deletedAt | LocalDateTime | nullable | 탈퇴 시점

«Entity» OwnerInfo
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
ownerInfoId | Long | PK, NOT NULL | 사장 회원 고유 식별자
account | Account | FK, UNIQUE, NOT NULL | 연관 회원 (1:1, @OneToOne LAZY)
businessNumber | String | UNIQUE, NOT NULL | 사업자 등록번호
openingDate | LocalDate | NOT NULL | 개업일자
approvalStatus | ApprovalStatus | NOT NULL | PENDING / APPROVED / REJECTED
rejectionReason | String | nullable | 거절 사유
reviewRequestedAt | LocalDateTime | nullable | 심사 요청 시각
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

> 설계 변경 사항: 기존 SDD에서 OwnerInfo에 있던 `phone` 필드는 실제 코드에 존재하지 않는다. 사장 연락처는 `Store.phone`(사업장 전화번호)으로 관리된다. 또한 `openingDate`(LocalDate) 및 `reviewRequestedAt` 필드가 추가되었다.

«Entity» AccountRegion
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
accountRegionId | Long | PK, NOT NULL | 회원 지역 고유 식별자
account | Account | FK, NOT NULL | 연관 회원 (@ManyToOne LAZY)
region | Region | FK, NOT NULL | 연관 지역 (@ManyToOne LAZY)
verified | boolean | NOT NULL | GPS 인증 완료 여부
verifiedAt | LocalDateTime | nullable | 인증 시각
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Region
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
regionId | Long | PK, NOT NULL | 지역 고유 식별자
regionCode | String | UNIQUE, NOT NULL | 행정동 코드
siDo | String | NOT NULL | 시/도
gunGu | String | NOT NULL | 시/군/구
dong | String | NOT NULL | 행정동명
radius | int | NOT NULL | 서비스 반경(m)

«Entity» Location
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
locationId | Long | PK, NOT NULL | 좌표 고유 식별자
regionId | Long | FK, NOT NULL | 연관 지역(1:1)
latitude | Double | NOT NULL | 위도
longitude | Double | NOT NULL | 경도

«Entity» AdminAuditLog

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

«Entity» Account (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
createUser(email, encodedPassword, name, nickname, phone) | Account | 일반 회원 생성 (ROLE_USER, ACTIVE)
createOAuthPendingUser(email, nickname, profileImageUrl, provider, providerId) | Account | OAuth 신규 회원 임시 생성 (ROLE_USER, PENDING — 추가 정보 미입력 상태)
createOwner(email, encodedPassword, name, phone) | Account | 사장 회원가입 신청 (ROLE_USER, ACTIVE — 관리자 승인 후 ROLE_OWNER로 변경)
completeOAuthProfile(name, phone, nickname) | void | OAuth 추가 정보 입력 완료 후 상태를 ACTIVE로 전환
approveOwner() | void | 역할을 ROLE_OWNER로 변경 (OwnerInfo.approve() 내부에서 호출)
activate() | void | 상태를 ACTIVE로 변경, deletedAt 초기화 (정지 해제 포함)
suspend() | void | 상태를 SUSPENDED로 변경
cancelWithdrawal() | void | 탈퇴 취소 (status=ACTIVE, deletedAt=null)
withdraw() | void | 상태를 WITHDRAWN으로 변경, deletedAt=now()
updateInfo(nickname, profileImageUrl) | void | 회원 정보 수정
changePassword(encoded) | void | 암호화된 새 비밀번호로 변경
isActive() | boolean | ACTIVE 상태 여부 반환
verifyEmail() | void | 이메일 인증 완료 처리 (emailVerified=true)
updateProfileImage(url) | void | 프로필 이미지 URL만 변경
updateFcmToken(fcmToken) | void | FCM 토큰 등록/갱신
isWithdrawn() | boolean | 탈퇴 여부 반환
isAdmin() | boolean | 관리자 여부 반환
isOwner() | boolean | 사장 여부 반환
isLocalAccount() | boolean | LOCAL 계정 여부 반환
isOAuthAccount() | boolean | OAuth 계정 여부 반환
setPrimaryRegion(accountRegionId) | void | Account.primaryRegionId를 AccountRegion PK로 설정
updatePrimaryRegion(accountRegionId) | void | primaryRegionId 갱신 (사장 승인 시 AccountRegion.accountRegionId로 설정)
clearPrimaryRegion() | void | 메인 지역 제거

«Entity» OwnerInfo (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
create(account, businessNumber, openingDate) | OwnerInfo | 사장 신청 생성 (PENDING)
requestReview() | void | 재심사 요청 (PENDING으로 재설정, reviewRequestedAt 갱신)
approve() | void | 상태를 APPROVED로 변경, account.approveOwner() 호출
reject(reason) | void | 상태를 REJECTED로 변경, 사유 저장
updateInfo(businessNumber) | void | 사업자번호 변경 시 재심사 상태로 전환
isBusinessVerified() | boolean | businessNumber, openingDate 유효 여부 반환

«Entity» AccountRegion (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
create(account, region) | AccountRegion | 지역 등록 (verified=false)
verify() | void | GPS 인증 완료 처리 (verified=true, verifiedAt=now())
isVerified() | boolean | 인증 여부 반환
isOwnedBy(accountId) | boolean | 소유자 여부 반환

### ● Controller

«Controller» AuthController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /auth/email/send-verification | ResponseEntity<ApiResponse<Void>> | 이메일 인증 코드 발송 (회원가입 전 이메일 소유 확인용 6자리 코드, Redis에 이메일과 확인용 코드 임시 저장 TTL 3분)
POST | /auth/email/verify | ResponseEntity<ApiResponse<String>> | 이메일 인증 코드 검증 (인증 성공 시 이메일 인증 토큰 반환 — signup 시 이 토큰 함께 전달)
POST | /auth/business/verify | ResponseEntity<ApiResponse<Boolean>> | 사업자등록번호 진위확인 (사장 회원가입 전 사업자번호, 대표자명, 개업일자 검증)
POST | /auth/signup | ResponseEntity<ApiResponse<Void>> | 일반 회원가입 (이메일 인증 토큰 필수)
POST | /auth/signup/owner | ResponseEntity<ApiResponse<Void>> | 사장 회원가입 신청 (이메일 인증 토큰 + 사업자 정보 필수, 동시에 Store 기본 정보 생성)
POST | /auth/signup/oauth | ResponseEntity<ApiResponse<TokenResponseDto>> | OAuth 신규 회원 추가 정보 입력 완료 (임시 토큰 + 이름/전화번호/닉네임 입력 후 가입 완료)
POST | /auth/login/oauth | ResponseEntity<ApiResponse<OAuthLoginResponseDto>> | OAuth 로그인 (기존 회원이면 토큰 반환, 신규 회원이면 tempToken 반환)
POST | /auth/login | ResponseEntity<ApiResponse<TokenResponseDto>> | 이메일/비밀번호 로그인 및 토큰 발급
POST | /auth/token/reissue | ResponseEntity<ApiResponse<TokenResponseDto>> | Refresh Token을 이용한 Access Token 재발급
POST | /auth/logout | ResponseEntity<ApiResponse<Void>> | 로그아웃 (Refresh Token 삭제 + Access Token 블랙리스트 등록)
POST | /auth/reauth | ResponseEntity<ApiResponse<ReAuthResponseDto>> | 재인증 요청 처리 (비밀번호 또는 OAuth 기반 검증 후 re-auth 토큰 발급)
POST | /auth/password/reset-request | ResponseEntity<ApiResponse<Void>> | 비밀번호 재설정 메일 발송 (이메일로 인증 코드 전송)
POST | /auth/password/verify | ResponseEntity<ApiResponse<String>> | 비밀번호 재설정 인증 코드 검증 (성공 시 password-reset 토큰 반환)
POST | /auth/password/reset | ResponseEntity<ApiResponse<Void>> | 토큰 기반 비밀번호 재설정 (로그인 없이 가능)

> 설계 변경 사항:
>
> - 기존 SDD의 `GET /auth/check-email`, `GET /auth/check-nickname` 엔드포인트는 실제 코드에 없다. 중복 확인은 signup 요청 처리 내부에서 수행된다.
> - `POST /auth/login/oauth` 응답이 `TokenResponseDto`에서 `OAuthLoginResponseDto`로 변경되었다. 신규 회원인 경우 `isNewUser=true`, `signupRequired=true`, `tempToken`을 반환하고, 기존 회원인 경우 토큰을 반환한다.
> - `POST /auth/password/reset-verify` → `POST /auth/password/verify`로 경로 변경. 응답 타입도 `CommonResponseDto`에서 `String`(password-reset 토큰)으로 변경되었다.
> - `POST /auth/business/verify`, `POST /auth/signup/oauth` 엔드포인트가 추가되었다.

«Controller» AccountController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /accounts/me | ResponseEntity<ApiResponse<MyPageResponseDto>> | 내 마이페이지 정보 조회 (활동 지역 포함)
PATCH | /accounts/me | ResponseEntity<ApiResponse<AccountResponseDto>> | 내 정보 수정 (닉네임/프로필 이미지, 재인증 불필요)
PATCH | /accounts/me/password | ResponseEntity<ApiResponse<Void>> | 비밀번호 변경 (재인증 토큰 + 현재 비밀번호 필요, LOCAL 계정만)
DELETE | /accounts/me | ResponseEntity<ApiResponse<Void>> | 회원 탈퇴 (재인증 토큰 필요, OWNER면 Store/상품 비활성화 선행)
PUT | /accounts/me/fcm-token | ResponseEntity<ApiResponse<Void>> | FCM 토큰 등록/갱신
GET | /accounts/me/owner | ResponseEntity<ApiResponse<OwnerApplicationDetailResponseDto>> | 내 사업자 정보 및 승인 상태 조회
PUT | /accounts/me/owner | ResponseEntity<ApiResponse<Void>> | 사장 정보 수정 (사업자번호 변경 시 재심사 상태로 전환)

> 설계 변경 사항:
>
> - 기존 SDD의 마이페이지 주문 내역, 리뷰 조회, 타인 프로필 조회 등 다수 엔드포인트가 AccountController에서 제거되고 각 도메인 컨트롤러로 이동하였다.
> - `POST /accounts/me/owner`(사장 정보 신규 등록)는 사장 회원가입(`/auth/signup/owner`) 흐름으로 통합되어 AccountController에 없다.
> - `PATCH /accounts/me/regions/{regionId}/primary`는 AccountRegionController의 `PATCH /accounts/me/regions/{accountRegionId}/primary`로 이동하였다.
> - `PUT /accounts/me/fcm-token` 엔드포인트가 추가되었다.

«Controller» AccountRegionController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /accounts/me/regions | ResponseEntity<ApiResponse<List<AccountRegionResponseDto>>> | 활동 지역 목록 조회
GET | /accounts/me/regions/{accountRegionId} | ResponseEntity<ApiResponse<AccountRegionResponseDto>> | 특정 활동 지역 조회
POST | /accounts/me/regions | ResponseEntity<ApiResponse<AccountRegionResponseDto>> | 활동 지역 등록 (최대 2개)
PATCH | /accounts/me/regions/{accountRegionId}/verify | ResponseEntity<ApiResponse<AccountRegionResponseDto>> | 활동 지역 GPS 인증 (Haversine 공식으로 거리 검증)
PATCH | /accounts/me/regions/{accountRegionId}/primary | ResponseEntity<ApiResponse<Void>> | 대표 활동 지역 설정 (인증된 지역만 가능)
DELETE | /accounts/me/regions/{accountRegionId} | ResponseEntity<ApiResponse<Void>> | 활동 지역 삭제

> 설계 변경 사항: 경로 파라미터가 `{regionId}`에서 `{accountRegionId}`(AccountRegion PK)로 변경되었다. 대표 지역 설정도 Region.regionId가 아닌 AccountRegion.accountRegionId 기준으로 동작한다.

«Controller» AdminAccountController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/accounts | ResponseEntity<ApiResponse<Page<AccountResponseDto>>> | 회원 목록 조회 (status/role/keyword 필터, QueryDSL 복합 검색)
GET | /admin/accounts/{accountId} | ResponseEntity<ApiResponse<AccountDetailResponseDto>> | 회원 상세 조회
GET | /admin/accounts/withdrawn | ResponseEntity<ApiResponse<Page<AccountResponseDto>>> | 탈퇴 예정 회원 목록
PATCH | /admin/accounts/{accountId}/suspend | ResponseEntity<ApiResponse<Void>> | 회원 정지 (SUSPENDED, Refresh Token 삭제)
PATCH | /admin/accounts/{accountId}/activate | ResponseEntity<ApiResponse<Void>> | 회원 정지 해제 (ACTIVE)
PATCH | /admin/accounts/{accountId}/withdrawal/cancel | ResponseEntity<ApiResponse<Void>> | 탈퇴 취소 (ACTIVE 복구)
DELETE | /admin/accounts/{accountId} | ResponseEntity<ApiResponse<Void>> | 강제 탈퇴 (WITHDRAWN 상태 변경 + deletedAt 기록 + Refresh Token 삭제 — 물리 삭제는 AccountCleanupScheduler에 위임, 30일 유예 적용)
GET | /admin/accounts/owners/applications | ResponseEntity<ApiResponse<Page<OwnerApplicationListResponseDto>>> | 사장 신청 목록 (approvalStatus/businessNumber/storeName/신청일 필터)
GET | /admin/accounts/owners/{ownerInfoId} | ResponseEntity<ApiResponse<OwnerApplicationDetailResponseDto>> | 사장 신청 상세 조회 (OwnerInfo + Store 정보 포함)
PATCH | /admin/accounts/owners/{ownerInfoId}/approve | ResponseEntity<ApiResponse<Void>> | 사장 승인 (APPROVED + ROLE_OWNER 변경 + 활동지역 자동 등록 + VisitReservationSetting 생성)
PATCH | /admin/accounts/owners/{ownerInfoId}/reject | ResponseEntity<ApiResponse<Void>> | 사장 거절 (REJECTED + 사유 저장)

> 설계 변경 사항:
>
> - 사장 관련 API 경로가 `/admin/owners/...`에서 `/admin/accounts/owners/...`로 변경되었다.
> - 사장 승인 시 Store 주소 기반 지역(Region) 자동 해석, AccountRegion 인증 등록, primaryRegionId 갱신, VisitReservationSetting 기본값 생성까지 단일 트랜잭션에서 처리한다.

«Controller» AdminAuditLogController

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

### ● Service

«Service» AuthService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
sendEmailVerificationCode(EmailSendRequestDto) | void | REQUIRED | RateLimit(이메일당 60초 1회) | 이메일 중복 확인→ 인증코드 발송
verifyEmailCode(EmailVerifyRequestDto) | String | REQUIRED | - | 인증코드 검증 → 이메일 인증 토큰 반환
signup(SignupRequestDto) | void | REQUIRED | EmailVerificationToken | 이메일 인증 토큰 검증→ 닉네임 중복 확인→ Account 저장(ACTIVE)→ 인증 토큰 소비
ownerSignup(OwnerSignupRequestDto) | void | REQUIRED | EmailVerificationToken | 이메일 인증 토큰 검증→ 사업자번호 중복 확인→ Account(ROLE_USER, ACTIVE) + OwnerInfo(PENDING) + Store + 기본 카테고리 생성
oauthLogin(OAuthLoginRequestDto) | OAuthLoginResponseDto | REQUIRED | ExternalApi | OAuth 코드로 기존 회원 조회→ 기존 회원이면 토큰 반환, 신규 회원이면 Redis에 임시 정보 저장(TTL 10분) 후 tempToken 반환
oauthComplete(OAuthCompleteRequestDto) | TokenResponseDto | REQUIRED | - | tempToken으로 Redis에서 OAuth 정보 조회→ Account.createOAuthPendingUser→ completeOAuthProfile(ACTIVE)→ 저장→ 토큰 발급
login(LoginRequestDto) | TokenResponseDto | REQUIRED | RateLimit(이메일당 5분 5회 실패 차단) | 이메일/비밀번호 검증→ ACTIVE 확인→ Access+Refresh Token 발급→ Redis 저장
reissue(ReissueRequestDto) | TokenResponseDto | REQUIRED | - | Refresh Token 검증(JWT+Redis)→ 계정 상태 확인→ 새 토큰 발급 (Refresh Token Rotation)
logout(ReissueRequestDto, authorizationHeader) | void | REQUIRED | - | Access Token 추출 + Refresh Token 검증→ 사용자 일치 확인→ Access Token 블랙리스트 + Refresh Token 삭제
reAuth(accountId, ReAuthRequestDto) | ReAuthResponseDto | REQUIRED | - | LOCAL이면 비밀번호 검증, OAuth면 oauthToken 검증→ re-auth 토큰 발급
sendPasswordResetEmail(PasswordResetRequestDto) | void | REQUIRED | RateLimit(이메일당 5분 1회) | OAuth 계정 차단→ 인증코드 이메일 발송
verifyResetPasswordEmailCode(EmailVerifyRequestDto) | String | REQUIRED | - | OAuth 계정 차단→ 인증코드 검증→ password-reset 토큰 발급
resetPassword(PasswordNewRequestDto) | void | REQUIRED | - | 비밀번호 일치 확인→ password-reset 토큰 검증→ OAuth 계정 차단→ 비밀번호 변경→ Refresh Token 삭제

«Service» AccountService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getMyPage(accountId) | MyPageResponseDto | readOnly | - | 내 마이페이지 정보 조회 (AccountRegion 포함)
updateMyInfo(accountId, UpdateInfoRequestDto) | AccountResponseDto | REQUIRED | - | 닉네임/프로필 이미지 수정 (닉네임 중복 확인 포함)
changePassword(accountId, ChangePasswordRequestDto) | void | REQUIRED | ReAuthToken | ReAuth 토큰 검증→ OAuth 차단→ 현재 비밀번호 확인→ 비밀번호 변경→ ReAuth/Refresh 토큰 삭제
withdraw(accountId, WithdrawRequestDto) | void | REQUIRED | ReAuthToken | ReAuth 토큰 검증→ OWNER면 Store/상품 비활성화→ WITHDRAWN 처리→ ReAuth/Refresh 토큰 삭제
updateFcmToken(accountId, fcmToken) | void | REQUIRED | - | FCM 토큰 등록/갱신
getMyOwnerInfo(accountId) | OwnerApplicationDetailResponseDto | readOnly | - | 내 사업자 정보 및 승인 상태 조회
updateOwnerInfo(accountId, OwnerInfoRequestDto) | void | REQUIRED | - | 사업자번호 변경 시 중복 확인→ updateInfo (사업자번호 변경이면 재심사 상태로 전환)

«Service» AdminAccountService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAccounts(Pageable, status, role, keyword) | Page<AccountResponseDto> | readOnly | Admin | 회원 목록 조회 (QueryDSL 복합 필터: status/role/keyword — 이메일/닉네임/이름/전화번호 검색)
getAccountDetail(accountId) | AccountDetailResponseDto | readOnly | Admin | 회원 상세 조회 (AccountRegion + OwnerInfo 포함)
getWithdrawnAccounts(Pageable) | Page<AccountResponseDto> | readOnly | Admin | WITHDRAWN 상태 회원 목록
suspendAccount(adminId, targetAccountId) | void | REQUIRED | Admin | 회원 정지 (WITHDRAWN 이면 예외) + Refresh Token 삭제
activateAccount(adminId, targetAccountId) | void | REQUIRED | Admin | 회원 정지 해제
cancelWithdrawal(adminId, targetAccountId) | void | REQUIRED | Admin | 탈퇴 취소 (WITHDRAWN 이 아니면 예외)
forceDeleteAccount(adminId, targetAccountId) | void | REQUIRED | Admin | 강제 탈퇴 처리 (WITHDRAWN + Refresh Token 삭제, 물리 삭제는 AccountCleanupScheduler에 위임)
getOwnerRequests(OwnerInfoSearchDto, Pageable) | Page<OwnerApplicationListResponseDto> | readOnly | Admin | 사장 신청 목록 (기본값 PENDING, QueryDSL 복합 필터)
getOwnerApplicationDetail(ownerInfoId) | OwnerApplicationDetailResponseDto | readOnly | Admin | 사장 신청 상세 (OwnerInfo + Store + 영업시간 포함)
approveOwner(adminId, ownerInfoId) | void | REQUIRED | Admin | Store 위치 해석→ Region 조정→ AccountRegion 인증 등록→ primaryRegionId 갱신→ OwnerInfo.approve()→ VisitReservationSetting 생성→ Refresh Token 삭제
rejectOwner(adminId, ownerInfoId, RejectRequestDto) | void | REQUIRED | Admin | OwnerInfo.reject(reason)

«Service» AccountRegionService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getRegions(accountId) | List<AccountRegionResponseDto> | readOnly | - | 활동 지역 목록 조회
getRegion(accountId, accountRegionId) | AccountRegionResponseDto | readOnly | Ownership | 특정 활동 지역 조회
addRegion(accountId, RegionRequestDto) | AccountRegionResponseDto | REQUIRED | MaxRegionLimit(2) | 최대 2개 제한→ 지역 존재 확인→ 중복 등록 확인→ 등록(verified=false)
verifyRegion(accountId, accountRegionId, LocationDto) | AccountRegionResponseDto | REQUIRED | Ownership + GpsValidation | Haversine 공식 거리 계산→ Region.radius 초과 시 예외→ verified=true→ primaryRegionId 미설정이면 자동 설정
setPrimaryRegion(accountId, accountRegionId) | void | REQUIRED | Ownership | 인증된 지역인지 검증 후 Account.primaryRegionId = accountRegionId
deleteRegion(accountId, accountRegionId) | void | REQUIRED | Ownership | 대표 지역 삭제 시 다른 인증 지역으로 자동 재설정, 없으면 null

«Service» TokenService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
saveRefreshToken(accountId, refreshToken) | void | - | - | Refresh Token Redis 저장
validateRefreshToken(refreshToken) | Long | - | - | JWT 유효성 + type 확인 + Redis 일치 여부 검증 → accountId 반환
deleteRefreshToken(accountId) | void | - | - | Refresh Token 삭제
generateAndSaveReAuthToken(accountId) | String | - | - | re-auth 토큰 생성 및 Redis 저장
validateReAuthToken(accountId, token) | void | - | - | re-auth 토큰 검증 (accountId 일치 포함)
consumeReAuthToken(accountId) | void | - | - | re-auth 토큰 삭제 (일회용 소비)
generateAndSavePasswordResetToken(accountId) | String | - | - | password-reset 토큰 생성 및 Redis 저장
validatePasswordResetToken(token) | Long | - | - | password-reset 토큰 검증 → accountId 반환 + Redis 삭제
deletePasswordResetToken(accountId) | void | - | - | password-reset 토큰 삭제
logout(accountId, accessToken) | void | - | - | Refresh Token 삭제 + Access Token 블랙리스트 등록

«Service» AdminAuditService

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

### ● Repository

«Repository» AccountRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long accountId) | Optional<Account> | 회원 ID로 조회
findByEmail(String email) | Optional<Account> | 이메일로 조회
existsByEmail(String email) | boolean | 이메일 중복 확인
existsByNickname(String nickname) | boolean | 닉네임 중복 확인
findByProviderAndProviderId(OAuthProvider, String) | Optional<Account> | OAuth 로그인 시 기존 회원 조회
findByStatus(AccountStatus, Pageable) | Page<Account> | 상태별 회원 목록 (탈퇴 예정 등)
findWithdrawnAccountsBefore(AccountStatus, LocalDateTime) | List<Account> | 탈퇴 후 N일 경과 계정 (AccountCleanupScheduler용, JPQL)
clearFcmTokenByFcmToken(String) | int | 무효/만료 FCM 토큰 초기화 (JPQL UPDATE)
findAllActiveAccountIds() | List<Long> | ACTIVE 계정 ID 전체 조회
findAdminAccountIds() | List<Long> | ROLE_ADMIN + ACTIVE 계정 ID 조회

«Repository» AccountRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchAccounts(AccountStatus, AccountRole, String keyword, Pageable) | Page<Account> | 관리자 복합 필터 (status/role/키워드 — 이메일/닉네임/이름/전화번호 포함)

«Repository» OwnerInfoRepository
메서드명 | 반환타입 | 설명
---|---|---
findByAccount_AccountId(Long) | Optional<OwnerInfo> | 회원 ID로 사장 정보 조회
existsByBusinessNumber(String) | boolean | 사업자번호 중복 확인
existsByAccount_AccountIdAndApprovalStatus(Long, ApprovalStatus) | boolean | 계정 + 승인 상태 존재 여부
existsByAccount_AccountId(Long) | boolean | 계정의 사장 정보 존재 여부
findByApprovalStatus(ApprovalStatus, Pageable) | Page<OwnerInfo> | 승인 상태별 목록
findByApprovalStatusAndReviewRequestedAtIsNotNull(ApprovalStatus, Pageable) | Page<OwnerInfo> | 재심사 요청 목록
findByAccount(Account) | Optional<OwnerInfo> | Account 객체로 조회
deleteByAccount_AccountId(Long) | void | 계정 삭제 시 사장 정보 연쇄 삭제

«Repository» OwnerInfoRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchOwnerApplications(OwnerInfoSearchDto, Pageable) | Page<OwnerApplicationListResponseDto> | 관리자 복합 필터 (approvalStatus/businessNumber/storeName/신청일 기간)

«Repository» AccountRegionRepository
메서드명 | 반환타입 | 설명
---|---|---
findByAccount_AccountId(Long) | List<AccountRegion> | 회원의 활동 지역 목록
findByAccountRegionIdAndAccount_AccountId(Long, Long) | Optional<AccountRegion> | accountRegionId + accountId로 소유 확인 포함 조회
existsByAccount_AccountIdAndRegion_RegionId(Long, Long) | boolean | 동일 지역 등록 여부
countByAccount_AccountId(Long) | int | 활동 지역 개수 (최대 2개 제한 확인)
findByAccount_AccountIdAndVerifiedTrueAndAccountRegionIdNot(Long, Long) | List<AccountRegion> | 대표 지역 삭제 후 재설정 대상 조회
deleteByAccount_AccountId(Long) | void | 계정 삭제 시 활동 지역 연쇄 삭제

> 설계 변경 사항: 기존 SDD의 일부 메서드명이 실제 코드와 다르게 기재되어 있었다. 특히 단건 조회 메서드는 `findByAccount_AccountIdAndRegion_RegionId`에서 `findByAccountRegionIdAndAccount_AccountId`로 변경되었다. FK 기준이 Region.regionId에서 AccountRegion.accountRegionId 기준으로 변경된 구조를 반영한다.

«Repository» RegionRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long regionId) | Optional<Region> | 지역 ID로 조회
findByRegionCode(String regionCode) | Optional<Region> | 행정동 코드로 조회
findAllBySiDoAndGunGu(String siDo, String gunGu) | List<Region> | 시/도 + 시/군/구 기준 지역 조회
findAllByDongContaining(String dong) | List<Region> | 행정동명 검색
existsByRegionCode(String regionCode) | boolean | 행정동 코드 존재 여부 확인

«Repository» LocationRepository
메서드명 | 반환타입 | 설명
---|---|---
findByRegion_RegionId(Long regionId) | Optional<Location> | 지역 ID로 대표 좌표 조회

«Repository» TokenRepository (Redis 기반, RedisUtil/RedisTemplate 직접 사용)
메서드명 | 반환타입 | 설명
---|---|---
saveRefreshToken(accountId, refreshToken, TTL) | void | Redis 저장 (키: refresh:{accountId})
findRefreshToken(accountId) | Optional<String> | Refresh Token 조회
deleteRefreshToken(accountId) | void | Refresh Token 삭제
saveReAuthToken(accountId, token, TTL) | void | re-auth 토큰 저장 (키: reauth:{accountId})
findReAuthToken(accountId) | Optional<String> | re-auth 토큰 조회
consumeReAuthToken(accountId) | void | re-auth 토큰 삭제 (일회용 소비)
savePasswordResetToken(accountId, token, TTL) | void | password-reset 토큰 저장 (키: password-reset:{accountId})
validateAndConsumePasswordResetToken(token) | Long | 검증 후 삭제
addTokenBlacklist(accessToken, TTL) | void | Access Token 블랙리스트 등록 (키: blacklist:access:{token})
isTokenBlacklisted(accessToken) | boolean | 블랙리스트 확인

«Repository» AdminAuditLogRepository

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

### ● Aspect

«Aspect» AdminAuditAspect

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

### ● Component

«Component» AccountCleanupScheduler (회원 탈퇴 정리)
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
cleanupWithdrawnAccounts() | void | - | @Scheduled(cron = "0 0 3 \* \* \*") | 매일 03:00 — 탈퇴 후 30일 경과 WITHDRAWN 계정 물리 삭제 + CASCADE

> 참고: 코드베이스 기준(CLAUDE.md)은 03:00이나, 기존 SDD 원문에는 02:00으로 기재되어 있었다. 코드베이스 기준으로 03:00으로 갱신한다.

«Component» AuditLogCleanupScheduler (감사 로그 정리)

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

## 5.8 ENUM (Account 관련)

### ● Account

«Enum» AccountRole
ENUM 값 | 설명
---|---
ROLE_USER | 일반 사용자
ROLE_OWNER | 사장 회원
ROLE_ADMIN | 관리자

«Enum» AccountStatus
ENUM 값 | 설명
---|---
PENDING | OAuth 가입 중간 단계 — 추가 정보 입력 미완료 상태 (createOAuthPendingUser 후 completeOAuthProfile 이전)
ACTIVE | 정상 활성 상태
SUSPENDED | 이용 정지
WITHDRAWN | 탈퇴 (30일 유예, AccountCleanupScheduler가 물리 삭제)

> 설계 변경 사항: 기존 SDD에는 `PENDING` 값이 누락되어 있었고, ENUM 값과 설명의 행 정렬도 어긋나 있었다. 실제 코드 기준으로 전체 수정하였다. `PENDING`은 OAuth 2단계 가입 플로우에서 신규 회원이 추가 정보를 입력하기 전의 임시 상태이다.

«Enum» OAuthProvider
ENUM 값 | 설명
---|---
LOCAL | 일반 로그인
KAKAO | 카카오 로그인

> 설계 변경 사항: 기존 SDD에는 `NAVER`가 포함되어 있었으나 실제 코드(`OAuthProvider.java`)에는 `LOCAL`, `KAKAO`만 존재한다.

«Enum» ApprovalStatus
ENUM 값 | 설명
---|---
PENDING | 승인 대기
APPROVED | 승인 완료
REJECTED | 승인 거절

«Enum» AuditResult, AuditActionType

> 상세 설계는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-1: 회원가입 (이메일 인증)
- DSEQ-2: 로그인 (JWT 발급)
- DSEQ-3: 토큰 재발급
- DSEQ-4: 비밀번호 재설정
- DSEQ-5: 활동지역 등록 (위치 인증)
- DSEQ-6: 회원 탈퇴 (Soft Delete)
- DSEQ-30: 사장 승인 (감사 로그)

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Account (회원)

| 컬럼명            | 타입         | 제약조건         | 설명                                                                |
| ----------------- | ------------ | ---------------- | ------------------------------------------------------------------- |
| account_id        | bigint       | PK               | 회원 고유 식별자                                                    |
| primary_region_id | bigint       | nullable, INDEX  | 대표 활동 지역 (AccountRegion.account_region_id 참조, FK 제약 없음) |
| email             | varchar(255) | UNIQUE           | 로그인 이메일                                                       |
| password          | varchar(255) | nullable         | BCrypt 암호화. OAuth 시 null                                        |
| name              | varchar(100) | NOT NULL         | 실명                                                                |
| phone             | varchar(20)  | NOT NULL         | 전화번호                                                            |
| provider          | varchar(20)  | NOT NULL         | OAuthProvider ENUM                                                  |
| provider_id       | varchar(255) | nullable         | OAuth 식별자. LOCAL이면 null                                        |
| profile_image_url | varchar(500) | nullable         | 프로필 이미지 URL                                                   |
| nickname          | varchar(50)  | UNIQUE, nullable | 닉네임 (PENDING 상태에서는 임시값)                                  |
| role              | varchar(20)  | NOT NULL         | AccountRole ENUM                                                    |
| status            | varchar(20)  | NOT NULL         | AccountStatus ENUM                                                  |
| email_verified    | boolean      | NOT NULL         | 이메일 인증 여부                                                    |
| fcm_token         | varchar(255) | nullable         | 푸시 발송용 디바이스 토큰                                           |
| created_at        | datetime     | NOT NULL         | BaseEntity 상속                                                     |
| modified_at       | datetime     | NOT NULL         | BaseEntity 상속                                                     |
| deleted_at        | datetime     | nullable         | 탈퇴 시점(Soft Delete, 30일 유예)                                   |

### OwnerInfo (사장 정보)

| 컬럼명              | 타입         | 제약조건                      | 설명                  |
| ------------------- | ------------ | ----------------------------- | --------------------- |
| owner_info_id       | bigint       | PK, NOT NULL, AUTO_INCREMENT  | 사장 회원 고유 식별자 |
| account_id          | bigint       | FK(account), UNIQUE, NOT NULL | 연관 회원(1:1)        |
| business_number     | varchar(50)  | UNIQUE, NOT NULL              | 사업자 등록번호       |
| opening_date        | date         | NOT NULL                      | 개업일자              |
| approval_status     | varchar(20)  | NOT NULL                      | ApprovalStatus ENUM   |
| rejection_reason    | varchar(255) | nullable                      | 거절 사유             |
| review_requested_at | datetime     | nullable                      | 심사 요청 시각        |
| created_at          | datetime     | NOT NULL                      | BaseEntity 상속       |
| modified_at         | datetime     | NOT NULL                      | BaseEntity 상속       |

> 설계 변경 사항: 기존 SDD의 `phone` 컬럼을 제거하고, `opening_date`, `review_requested_at` 컬럼을 추가하였다.

### AccountRegion (회원 지역)

| 컬럼명            | 타입     | 설명                         | 설명                  |
| ----------------- | -------- | ---------------------------- | --------------------- |
| account_region_id | bigint   | PK, NOT NULL, AUTO_INCREMENT | 회원 지역 고유 식별자 |
| account_id        | bigint   | FK(account), NOT NULL        | 연관 회원             |
| region_id         | bigint   | FK(region), NOT NULL         | 연관 지역             |
| verified          | boolean  | NOT NULL, DEFAULT false      | GPS 인증 완료 여부    |
| verified_at       | datetime | nullable                     | 인증 시각             |
| created_at        | datetime | NOT NULL                     | BaseEntity 상속       |
| modified_at       | datetime | NOT NULL                     | BaseEntity 상속       |

UNIQUE 제약: (account_id, region_id) — 동일 계정에 동일 지역 중복 등록 불가

### Region (지역)

| 컬럼명      | 타입        | 설명                         | 설명             |
| ----------- | ----------- | ---------------------------- | ---------------- |
| region_id   | bigint      | PK, NOT NULL, AUTO_INCREMENT | 지역 고유 식별자 |
| region_code | varchar(20) | UNIQUE, NOT NULL             | 행정동 코드      |
| si_do       | varchar(50) | NOT NULL                     | 시/도            |
| gun_gu      | varchar(50) | NOT NULL                     | 시/군/구         |
| dong        | varchar(50) | NOT NULL                     | 행정동명         |
| radius      | int         | NOT NULL                     | 서비스 반경(m)   |

### Location (지역 좌표)

| 컬럼명      | 타입   | 설명                         | 설명             |
| ----------- | ------ | ---------------------------- | ---------------- |
| location_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 좌표 고유 식별자 |
| region_id   | bigint | FK(region), UNIQUE, NOT NULL | 연관 지역(1:1)   |
| latitude    | double | NOT NULL                     | 위도             |
| longitude   | double | NOT NULL                     | 경도             |

### AdminAuditLog (관리자 감사 로그)

> 상세 테이블 정의는 `common.md`의 "관리자 감사 로그 구현 상태" 참조.

## 9.2 도메인 ERD (참조)

● Account / Region 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 설계 변경 사항 요약

### forceDeleteAccount 동작 명확화

기존 SDD 일부 버전에서는 `forceDeleteAccount`(관리자 강제 탈퇴)가 "30일 유예 무시, 즉시 물리 삭제"로 기술된 경우가 있었다.

실제 코드 (`AdminAccountService.forceDeleteAccount`)는 다음과 같이 동작한다:

1. `Account.withdraw()` 호출 — 상태를 `WITHDRAWN`으로 변경하고 `deletedAt = now()` 기록
2. `tokenService.deleteRefreshToken(targetAccountId)` — Refresh Token 삭제 (강제 세션 종료)
3. 물리 삭제(DB 레코드 제거)는 수행하지 않으며, `AccountCleanupScheduler`(매일 03:00)가 `deletedAt` 기준 30일 경과 후 일괄 처리

즉, 관리자 강제 탈퇴도 일반 회원 자진 탈퇴와 동일한 30일 유예 소프트 딜리트 흐름을 따른다. 다만 `cancelWithdrawal` API를 통해 30일 내에 탈퇴 취소(ACTIVE 복구)가 가능하다.

### OAuth 2단계 가입 플로우 추가

기존 SDD에서는 OAuth 로그인 시 바로 토큰을 발급하거나 신규 회원을 즉시 생성하는 단일 단계로 설계되었다.

실제 구현에서는 다음 2단계 흐름으로 처리한다:

1. `POST /auth/login/oauth` — OAuth 공급자 토큰으로 사용자 식별. 기존 회원이면 즉시 토큰 반환. 신규 회원이면 OAuth 정보를 Redis에 10분간 저장하고 `tempToken` 반환 (`OAuthLoginResponseDto.newUser`)
2. `POST /auth/signup/oauth` — 신규 회원이 `tempToken` + 이름/전화번호/닉네임 입력 후 `oauthComplete` 호출 → `Account.createOAuthPendingUser` 생성 후 `completeOAuthProfile`로 ACTIVE 전환 → 토큰 발급

이 흐름을 통해 `AccountStatus.PENDING`이 OAuth 중간 단계를 표현한다.

### primaryRegionId 참조 대상 수정

기존 SDD에서 `Account.primaryRegionId`는 `Region` 엔티티 FK로 명시되어 있었다.

실제 코드에서는 `AccountRegion.accountRegionId`(AccountRegion PK)를 저장한다. DB에 FK 제약은 없으며 서비스 레이어에서 관리된다. 실제 코드에서는 AccountRegion.accountRegionId(AccountRegion PK)를 저장한다.
DB에 FK 제약은 없으며 서비스 레이어에서 관리된다.
사장 승인 시에도 Store 주소 기반 Region을 찾은 뒤 AccountRegion을 조회 또는 생성하고,
생성된 AccountRegion.accountRegionId를 Account.primaryRegionId에 저장한다.AccountRegionController의 `setPrimaryRegion`은 AccountRegion PK를 저장한다.

### 사장 승인 시 활동지역 자동 인증 등록

기존 SDD에서는 사장 승인 시 role 변경과 approvalStatus 변경만 기술되어 있었다.

실제 구현에서는 사장 승인 시 다음 처리가 단일 트랜잭션에서 추가로 수행된다:

1. Store 주소 기반 지역(Region) 자동 해석 (`StoreLocationResolver`)
2. AccountRegion 생성 및 인증 등록 (`verified=true`) — 중복이 없을 때만
3. `Account.primaryRegionId` 갱신 (AccountRegion.accountRegionId로 설정)
4. `VisitReservationSetting` 기본값 생성 — 없을 때만
5. 기존 Refresh Token 삭제 (새 role 반영을 위한 재로그인 유도)

### OwnerInfo 구조 변경

기존 SDD: `phone`(String) 필드 포함, `openingDate`/`reviewRequestedAt` 미포함.

실제 코드: `phone` 필드 없음, `openingDate`(LocalDate), `reviewRequestedAt`(LocalDateTime) 추가. 사장 연락처는 `Store.phone`(사업장 전화번호)으로 관리된다.

### 사장 회원가입 시 Store 동시 생성

기존 SDD에서는 사장 회원가입 시 Account + OwnerInfo만 생성하는 것으로 기술되어 있었다.

실제 구현에서는 `ownerSignup` 트랜잭션 내에서 Store 기본 정보 생성 (`Store.createForOwnerSignup`) 및 기본 ProductCategory 생성(`productCategoryService.createDefaultCategories`)까지 함께 처리한다.

## 중복 참조 (common.md에도 기재됨)

- 동시성 제어 4계층, Redis 분산 락 명명 규칙은 `common.md` 참조
- JWT 토큰 정책(Access 30분 / Refresh 14일 / Re-auth / 이메일 인증 3분 / 비밀번호 재설정 10분)은 `common.md` 6.10.6 참조
- 민감 정보 마스킹 규칙(이메일/전화번호/이름)은 `common.md` 6.10.5, 6.13.4 참조
- Account Soft Delete 흐름(6.6.3)은 `common.md` 참조
- 관리자 감사 로그(AdminAuditLog, AdminAuditAspect, AdminAuditService, AuditActionType, AuditResult) 상세 설계는 `common.md` 참조
