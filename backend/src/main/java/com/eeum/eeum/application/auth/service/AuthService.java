package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.*;
import com.eeum.eeum.application.auth.dto.response.OAuthLoginResponseDto;
import com.eeum.eeum.application.auth.dto.response.OAuthUserInfo;
import com.eeum.eeum.application.auth.dto.response.ReAuthResponseDto;
import com.eeum.eeum.application.auth.dto.response.TokenResponseDto;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.application.product.service.ProductCategoryService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration EMAIL_VERIFICATION_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration PASSWORD_RESET_COOLDOWN = Duration.ofMinutes(5);
    private static final int LOGIN_FAIL_MAX_ATTEMPTS = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(5);

    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final OAuthService oAuthService;
    private final RedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessVerificationService businessVerificationService;
    private final ProductCategoryService productCategoryService;
    private final RateLimitService rateLimitService;
    private final RedisLockService redisLockService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountLogoutService accountLogoutService;
    private final AuthAccountReader authAccountReader;
    private final com.eeum.eeum.application.account.service.AccountWriteTransactions accountWriteTransactions;

    // ===================== 이메일 인증 =====================

    //회원가입 전 이메일 인증 코드를 발송하는 메서드
    public void sendEmailVerificationCode(EmailSendRequestDto request) {
        if (authAccountReader.emailExists(request.getEmail())) { //이메일이 이미 회원 DB에 존재하는지 확인
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }

        rateLimitService.checkCooldown( //같은 이메일로 60초에 1회만 발송 허용
                RateLimitKeys.emailVerification(request.getEmail()),
                EMAIL_VERIFICATION_COOLDOWN,
                ErrorCode.AUTH_RATE_LIMITED
        );

        emailService.sendVerificationCode(request.getEmail()); //이메일 인증 코드 발송
    }

    @Transactional
    //사용자가 입력한 이메일 인증 코드를 검증 메서드
    public String verifyEmailCode(EmailVerifyRequestDto request) {
        return emailService.verifyCodeAndIssueToken(request.getEmail(), request.getCode());
    }

    // ===================== 회원가입 =====================

    @Transactional
    public void signup(SignupRequestDto request) {
        validateSignupEmail(request.getEmail(), request.getEmailVerificationToken());
        validateNicknameNotDuplicated(request.getNickname());


        // 4. 계정 생성
        Account account = Account.createUser(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getNickname(),
                request.getPhone()
        );

        accountRepository.save(account);

        emailService.consumeVerificationToken(request.getEmailVerificationToken());

        log.info("일반 회원가입 완료: accountId={}", account.getAccountId());
    }

    public void ownerSignup(OwnerSignupRequestDto request) {
        String number = normalizeBusinessNumber(request.getBusinessNumber());
        LocalDate openingDate = parseOpeningDate(request.getOpeningDate());
        if (!businessVerificationService.verifyBusiness(number, request.getName(), openingDate.toString())) {
            throw new BusinessException(ErrorCode.BUSINESS_VERIFY_FAILED);
        }
        accountWriteTransactions.run(() -> registerVerifiedOwner(request));
    }

    private void registerVerifiedOwner(OwnerSignupRequestDto request) {
        validateSignupEmail(request.getEmail(), request.getEmailVerificationToken());

        // 3. 사업자번호 정규화
        String businessNumber = normalizeBusinessNumber(request.getBusinessNumber());

        // 4. 중복 확인

        if (ownerInfoRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        // 5. 개업일자 변환
        LocalDate openingDate = parseOpeningDate(request.getOpeningDate());

        // 7. 계정 생성
        // 사장 회원가입 신청 시점에는 ROLE_USER로 생성하고, 관리자 승인 후 ROLE_OWNER로 변경
        Account account = Account.createOwner(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getPhone()
        );

        accountRepository.save(account);

        // 8. 사장 정보 등록
        OwnerInfo ownerInfo = OwnerInfo.create(
                account,
                businessNumber,
                openingDate
        );

        ownerInfo.markBusinessVerified(request.getName());
        ownerInfoRepository.save(ownerInfo);

        // 9. 상점 기본 정보 등록
        Store store = Store.createForOwnerSignup(
                account,
                request.getStoreName(),
                request.getStoreAddress(),
                request.getStorePhone()
        );

        storeRepository.save(store);

        productCategoryService.createDefaultCategories(store);

        emailService.consumeVerificationToken(request.getEmailVerificationToken());


        log.info("사장 회원가입 신청 완료: accountId={}", account.getAccountId());
    }

    // ===================== 로그인 =====================

    public TokenResponseDto login(LoginRequestDto request) {
        String loginFailKey = RateLimitKeys.loginFail(request.getEmail());

        // 5분 동안 5회 이상 실패한 이메일은 비밀번호 확인 없이 바로 차단
        rateLimitService.checkNotBlocked(
                loginFailKey, LOGIN_FAIL_MAX_ATTEMPTS, LOGIN_FAIL_WINDOW, ErrorCode.AUTH_RATE_LIMITED);

        Account account = accountRepository.findByEmail(request.getEmail()).orElse(null);

        if (account == null) {
            rateLimitService.recordFailure(loginFailKey, LOGIN_FAIL_WINDOW);
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // OAuth 계정은 로컬 비밀번호 로그인을 허용하지 않음
        if (account.isOAuthAccount()) {
            rateLimitService.recordFailure(loginFailKey, LOGIN_FAIL_WINDOW);
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        validateAccountStatus(account); //계정 상태를 검사

        //사용자가 입력한 비밀번호와 DB에 저장된 암호화된 비밀번호가 일치하는지 확인
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            rateLimitService.recordFailure(loginFailKey, LOGIN_FAIL_WINDOW);
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        rateLimitService.resetFailure(loginFailKey); //로그인 성공 시 실패 카운트 초기화

        return issueTokens(account);
    }

    // ===================== OAuth로그인 =====================

    public OAuthLoginResponseDto oauthLogin(OAuthLoginRequestDto request) {
        OAuthUserInfo userInfo = oAuthService.getUserInfo(request.getProvider(), request.getAccessToken());

        Optional<Account> existing = accountRepository
                .findByProviderAndProviderId(request.getProvider(), userInfo.getProviderId());

        if (existing.isPresent()) {
            Account account = existing.get();
            validateAccountStatus(account);
            TokenResponseDto tokens = issueTokens(account);
            return OAuthLoginResponseDto.existingUser(tokens);
        }

        String tempToken = UUID.randomUUID().toString();

        String userInfoJson;
        try {
            userInfoJson = objectMapper.writeValueAsString(userInfo);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        redisTemplate.opsForValue().set(
                "oauth:temp:" + tempToken,
                userInfoJson,
                10, TimeUnit.MINUTES
        );

        return OAuthLoginResponseDto.newUser(tempToken);
    }

    // ===================== Oauth 신규회원 추가 정보 입력 가입 완료 =====================
    @Transactional
    public TokenResponseDto oauthComplete(OAuthCompleteRequestDto request) {
        // oauth:temp 삭제는 DB 커밋 성공 후 AFTER_COMMIT 이벤트로 처리
        // → 토큰 발급 실패나 DB 롤백 시 tempToken이 남아 재시도 가능
        String userInfoJson = (String) redisTemplate.opsForValue()
                .get("oauth:temp:" + request.getTempToken());

        if (userInfoJson == null) {
            throw new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN);
        }

        OAuthUserInfo userInfo;
        try {
            userInfo = objectMapper.readValue(userInfoJson, OAuthUserInfo.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        OAuthProvider provider = userInfo.getProvider();

        // 같은 OAuth 계정으로 이미 가입된 계정이 있는지 재확인
        // tempToken을 여러 번 쓰거나, 중복 요청이 들어오는 상황 방지
        accountRepository.findByProviderAndProviderId(
                provider,
                userInfo.getProviderId()
        ).ifPresent(account -> {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        });

        String name = request.getName();
        String phone = request.getPhone();

        String nickname = resolveNickname(
                request.getNickname() != null
                        ? request.getNickname()
                        : userInfo.getNickname()
        );

        Account account = Account.createOAuthPendingUser(
                userInfo.getEmail(),
                nickname,
                userInfo.getProfileImage(),
                provider,
                userInfo.getProviderId()
        );

        account.applyOAuthEmailVerification(userInfo.isEmailVerified());
        account.completeOAuthProfile(
                name,
                phone,
                nickname
        );

        accountRepository.save(account);

        eventPublisher.publishEvent(AccountTokenCleanupEvent.oauthTemp(request.getTempToken()));

        return issueTokens(account);
    }

    private String resolveNickname(String requested) {
        if (requested == null || accountRepository.existsByNickname(requested)) {
            return "이음_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return requested;
    }

    // ===================== 토큰 재발급 =====================

    /**
     * Refresh Token 재발급.
     *
     * Redis lock은 logout과의 경쟁(validate → save 사이 logout 개입)을 막는다.
     *
     * 제재와의 경쟁은 잠금이 아니라 토큰 세대로 푼다. 여기서 계정을 읽어 세대 N을
     * 확인한 뒤 정지가 커밋돼 세대가 N+1이 되더라도, 이 요청이 발급하는 토큰은 N을 실은 채
     * 나가므로 다음 검증에서 걸린다. 행 잠금으로 직렬화할 필요가 없다.
     */
    public TokenResponseDto reissue(ReissueRequestDto request) {
        // JWT 기본 형식/타입만 lock 밖에서 먼저 검증 (빠른 실패)
        if (!jwtProvider.isValid(request.getRefreshToken()) || !jwtProvider.isRefreshToken(request.getRefreshToken())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        Long accountId = jwtProvider.getAccountId(request.getRefreshToken());

        // Redis 저장값 일치 확인 + 새 토큰 저장을 원자적으로 처리
        return redisLockService.executeWithLock(
                LockKeys.reissue(accountId),
                Duration.ofSeconds(5),
                () -> {
                    Long validatedId = tokenService.validateRefreshToken(request.getRefreshToken());
                    Account account = accountRepository.findById(validatedId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
                    validateAccountStatus(account);

                    // 회수된 세대의 토큰이면 Redis에 남아 있어도 재발급하지 않는다.
                    if (!account.isTokenVersionCurrent(
                            jwtProvider.getTokenVersion(request.getRefreshToken()))) {
                        throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
                    }

                    return issueTokens(account);
                }
        );
    }

    // ===================== 로그아웃 =====================

    public void logout(ReissueRequestDto request, String authorizationHeader) {
        String accessToken = jwtProvider.resolveAccessToken(authorizationHeader);
        String refreshToken = request.getRefreshToken();
        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        Long accountId = jwtProvider.getAccountId(accessToken);
        if (!accountId.equals(jwtProvider.getAccountId(refreshToken))) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        redisLockService.executeWithLock(LockKeys.reissue(accountId), Duration.ofSeconds(5), () -> {
            accountLogoutService.logout(accountId, accessToken, refreshToken);
            return null;
        });
    }

    // ===================== 비밀번호 재설정 =====================

    public void sendPasswordResetEmail(PasswordResetRequestDto request) {
        Account account = authAccountReader.byEmail(request.getEmail());

        // 정지·탈퇴 계정은 재설정해도 로그인할 수 없다. 탈퇴 취소는 관리자만 할 수 있어
        // 비밀번호를 되찾아야 할 이유도 없다. 익명화 전(30일)이면 메일이 실제 수신함에 도착하므로
        // 발송 단계에서 막는다.
        account.assertWritable();

        // OAuth 계정은 로컬 비밀번호 재설정을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        rateLimitService.checkCooldown( //같은 이메일로 5분에 1회만 발송 허용
                RateLimitKeys.passwordReset(account.getEmail()),
                PASSWORD_RESET_COOLDOWN,
                ErrorCode.AUTH_RATE_LIMITED
        );

        // 비밀번호 재설정 이메일 발송
        emailService.sendPasswordResetEmail(account.getEmail());
    }

    @Transactional
    public void resetPassword(PasswordNewRequestDto request) {

        // 1. 비밀번호가 다른지 검증 — 반드시 토큰 소비(2번)보다 앞에 있어야 한다.
        //    뒤로 옮기면 확인란 오타 한 번에 재설정 토큰이 소비돼 사용자가 메일부터 다시 받아야 한다.
        //    형식 위반(8자·영문·숫자·특수문자)은 컨트롤러 @Valid가 서비스 진입 전에 거른다.
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 2. Password Reset Token 검증
        // - JWT 유효성
        // - type == PASSWORD_RESET
        // - Redis 저장값과 일치 여부 확인
        // - 검증 성공 시 Redis에서 삭제
        Long accountId = tokenService.consumePasswordResetToken(request.getPasswordResetToken());

        // 3. 회원 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 4. 정지·탈퇴 계정은 비밀번호를 바꿔주지 않는다.
        //    발송 시점에는 활성이었어도 토큰 유효 시간 안에 정지될 수 있어 여기서 다시 본다.
        account.assertWritable();

        //    회수된 세대의 토큰이면 거부한다. Redis 삭제는 비동기라 유실될 수 있어,
        //    "정지 전에 받은 재설정 토큰 → 재활성화 → 비밀번호 변경" 경로가 열린다.
        if (!account.isTokenVersionCurrent(
                jwtProvider.getTokenVersion(request.getPasswordResetToken()))) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN);
        }

        // 5. OAuth 계정은 로컬 비밀번호 재설정을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 6. 비밀번호 변경
        account.changePassword(passwordEncoder.encode(request.getNewPassword()));

        // 7. DB 커밋 성공 후 Refresh Token 삭제 — 기존 세션을 끊어 새 비밀번호로 다시 로그인하게 한다.
        // Password Reset Token은 2번에서 이미 소비됐다. 롤백돼도 되살아나지 않으므로
        // 재설정이 실패하면 메일을 다시 받아야 한다 — 일회용 보장을 위해 감수한 대가다.
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(accountId));

        log.info("비밀번호 재설정 완료: accountId={}", accountId);
    }

    @Transactional
    //사용자가 입력한 이메일 인증 코드를 검증 메서드
    public String verifyResetPasswordEmailCode(EmailVerifyRequestDto request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 1. 인증코드 검증
        emailService.verifyPasswordResetCode(request.getEmail(), request.getCode());

        // 2. 검증 성공 후 JWT password reset token 발급
        return tokenService.generateAndSavePasswordResetToken(
                account.getAccountId(), account.getTokenVersion());
    }

    // ===================== 재인증 =====================

    public ReAuthResponseDto reAuth(Long accountId, ReAuthRequestDto request) {
        Account account = authAccountReader.byId(accountId);

        validateAccountStatus(account);

        if (account.isLocalAccount()) {
            validateLocalReAuth(account, request);
        } else {
            validateOAuthReAuth(account, request);
        }

        String reAuthToken = tokenService.generateAndSaveReAuthToken(
                accountId, account.getTokenVersion());

        return ReAuthResponseDto.builder()
                .reAuthToken(reAuthToken)
                .expiresIn(jwtProvider.getReauthTokenExpiration())
                .build();
    }

    // ===================== 내부 유틸 =====================

    private void validateSignupEmail(String requestEmail, String verificationToken) {
        String verifiedEmail = emailService.validateVerificationToken(verificationToken);

        if (!verifiedEmail.equals(requestEmail)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        if (accountRepository.existsByEmail(requestEmail)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }
    }

    private void validateNicknameNotDuplicated(String nickname) {
        if (accountRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_NICKNAME);
        }
    }

    private void validateLocalReAuth(Account account, ReAuthRequestDto request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }
    }

    private void validateOAuthReAuth(Account account, ReAuthRequestDto request) {
        if (request.getOauthToken() == null || request.getOauthToken().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        oAuthService.validateToken(account.getProvider(), request.getOauthToken(), account.getProviderId());
    }

    private LocalDate parseOpeningDate(String openingDate) {
        try {
            return LocalDate.parse(
                    openingDate,
                    DateTimeFormatter.ofPattern("yyyyMMdd")
            );
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BUSINESS_INVALID_OPENING_DATE);
        }
    }

    private void validateAccountStatus(Account account) {
        if (account.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
    }

    private String normalizeBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        return businessNumber.replaceAll("[^0-9]", "");
    }

    private TokenResponseDto issueTokens(Account account) {
        // 발급 시점의 세대를 박는다. 발급 도중 제재가 커밋돼 세대가 오르면
        // 이 토큰은 낡은 세대를 실은 채로 나가 검증에서 걸린다 — 잠금 없이 경쟁이 해소된다.
        String accessToken = jwtProvider.generateAccessToken(
                account.getAccountId(),
                account.getRole().name(),
                account.getTokenVersion()
        );

        String refreshToken = jwtProvider.generateRefreshToken(
                account.getAccountId(), account.getTokenVersion());

        tokenService.saveRefreshToken(account.getAccountId(), refreshToken);

        Optional<OwnerInfo> ownerInfoOpt =
                ownerInfoRepository.findByAccount_AccountId(account.getAccountId());

        boolean ownerInfoExists = ownerInfoOpt.isPresent();

        String ownerApprovalStatus = ownerInfoOpt
                .map(ownerInfo -> ownerInfo.getApprovalStatus() == null
                        ? null
                        : ownerInfo.getApprovalStatus().name())
                .orElse(null);

        boolean canAccessApprovalPage =
                account.getRole() == AccountRole.ROLE_USER
                        && ownerInfoOpt
                        .map(ownerInfo ->
                                ownerInfo.getApprovalStatus() == ApprovalStatus.PENDING
                                        || ownerInfo.getApprovalStatus() == ApprovalStatus.REJECTED
                        )
                        .orElse(false);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtProvider.getAccessTokenExpiration())
                .refreshTokenExpiresIn(jwtProvider.getRefreshTokenExpiration())
                .role(account.getRole().name())
                .ownerInfoExists(ownerInfoExists)
                .ownerApprovalStatus(ownerApprovalStatus)
                .aiPlanType(resolveAiPlanType(account))
                .build();
    }

    // 세션 표시용 값이다. AI 기능 권한은 요청마다 AiManagerSupportService가 DB 기준으로 재검증한다.
    private String resolveAiPlanType(Account account) {
        if (account.getRole() != AccountRole.ROLE_OWNER) {
            return null;
        }

        return storeRepository.findByAccount_AccountId(account.getAccountId())
                .map(Store::getStoreId)
                .map(this::getCurrentPlanType)
                .orElse(AiPlanType.FREE.name());
    }

    private String getCurrentPlanType(Long storeId) {
        return aiPlanSubscriptionRepository
                .findCurrentActivePlans(storeId, java.time.LocalDateTime.now(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(AiPlanSubscription::getPlanType)
                .map(Enum::name)
                .orElse(AiPlanType.FREE.name());
    }
}
