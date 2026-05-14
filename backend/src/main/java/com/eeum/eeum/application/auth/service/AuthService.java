package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.*;
import com.eeum.eeum.application.auth.dto.response.OAuthLoginResponseDto;
import com.eeum.eeum.application.auth.dto.response.OAuthUserInfo;
import com.eeum.eeum.application.auth.dto.response.ReAuthResponseDto;
import com.eeum.eeum.application.auth.dto.response.TokenResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final OAuthService oAuthService;
    private final RedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessVerificationService businessVerificationService;

    // ===================== 이메일 인증 =====================

    @Transactional
    //회원가입 전 이메일 인증 코드를 발송하는 메서드
    public void sendEmailVerificationCode(EmailSendRequestDto request) {
        if (accountRepository.existsByEmail(request.getEmail())) { //이메일이 이미 회원 DB에 존재하는지 확인
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }

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
        // 1. 이메일 인증 토큰 검증 → 이메일 추출
        String verifiedEmail = emailService.validateVerificationToken(
                request.getEmailVerificationToken()
        );

        // 2. 요청 이메일과 인증된 이메일 일치 확인
        if (!verifiedEmail.equals(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        // 3. 이메일 중복 확인
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }

        // 4. 닉네임 중복 확인
        if (accountRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_NICKNAME);
        }

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

    @Transactional
    public void ownerSignup(OwnerSignupRequestDto request) {
        // 1. 이메일 인증 토큰 검증 → 이메일 추출
        String verifiedEmail = emailService.validateVerificationToken(
                request.getEmailVerificationToken()
        );

        // 2. 요청 이메일과 인증된 이메일 일치 확인
        if (!verifiedEmail.equals(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        // 3. 사업자번호 정규화
        String businessNumber = request.getBusinessNumber().replace("-", "");

        // 4. 중복 확인
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }

        if (ownerInfoRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        // 5. 개업일자 변환
        LocalDate openingDate = parseOpeningDate(request.getOpeningDate());

        // 6. 국세청 사업자등록정보 진위확인
/*        boolean verified = businessVerificationService.verifyBusiness(
                businessNumber,
                request.getName(),          // 대표자명
                request.getOpeningDate()    // yyyyMMdd 또는 yyyy-MM-dd
        );

        if (!verified) {
            throw new BusinessException(ErrorCode.BUSINESS_VERIFY_FAILED);
        } */

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

        ownerInfoRepository.save(ownerInfo);

        // 9. 상점 기본 정보 등록
        Store store = Store.createForOwnerSignup(
                account,
                request.getStoreName(),
                request.getStoreAddress(),
                request.getStorePhone()
        );

        storeRepository.save(store);

        emailService.consumeVerificationToken(request.getEmailVerificationToken());


        log.info("사장 회원가입 신청 완료: accountId={}", account.getAccountId());
    }

    // ===================== 로그인 =====================

    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD));

        // OAuth 계정은 로컬 비밀번호 로그인을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        validateAccountStatus(account); //계정 상태를 검사

        //사용자가 입력한 비밀번호와 DB에 저장된 암호화된 비밀번호가 일치하는지 확인
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        return issueTokens(account);
    }

    // ===================== OAuth로그인 =====================

    @Transactional
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

        account.completeOAuthProfile(
                name,
                phone,
                nickname
        );

        accountRepository.save(account);

        redisTemplate.delete("oauth:temp:" + request.getTempToken());

        return issueTokens(account);
    }

    private String resolveNickname(String requested) {
        if (requested == null || accountRepository.existsByNickname(requested)) {
            return "이음_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return requested;
    }

    // ===================== 토큰 재발급 =====================

    @Transactional
    public TokenResponseDto reissue(ReissueRequestDto request) {
        // 1. Refresh Token 검증
        // - JWT 유효성
        // - type == REFRESH
        // - Redis 저장값과 일치 여부 확인
        Long accountId = tokenService.validateRefreshToken(request.getRefreshToken());

        // 2. 회원 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 3. 계정 상태 검증
        validateAccountStatus(account);

        // 4. Access Token + Refresh Token 재발급
        // issueTokens 내부에서 새 Refresh Token을 Redis에 저장하므로 Refresh Token Rotation이 적용됨
        return issueTokens(account);
    }

    // ===================== 로그아웃 =====================

    @Transactional
    public void logout(ReissueRequestDto request, String authorizationHeader) {
        // 1. Authorization Header에서 Access Token 추출 및 검증
        String accessToken = jwtProvider.resolveAccessToken(authorizationHeader);

        // 2. Refresh Token 검증 후 accountId 추출
        Long refreshAccountId = tokenService.validateRefreshToken(request.getRefreshToken());

        // 3. Access Token의 accountId 추출
        Long accessAccountId = jwtProvider.getAccountId(accessToken);

        // 4. Access Token과 Refresh Token의 사용자 일치 확인
        if (!refreshAccountId.equals(accessAccountId)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 5. Access Token 블랙리스트 등록 + Refresh Token 삭제
        tokenService.logout(refreshAccountId, accessToken);

        log.info("로그아웃 완료: accountId={}", refreshAccountId);
    }

    // ===================== 비밀번호 재설정 =====================

    @Transactional
    public void sendPasswordResetEmail(PasswordResetRequestDto request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // OAuth 계정은 로컬 비밀번호 재설정을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 비밀번호 재설정 이메일 발송
        emailService.sendPasswordResetEmail(account.getEmail());
    }

    @Transactional
    public void resetPassword(PasswordNewRequestDto request) {

        // 1. 비밀번호가 다른지 검증
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 2. Password Reset Token 검증
        // - JWT 유효성
        // - type == PASSWORD_RESET
        // - Redis 저장값과 일치 여부 확인
        // - 검증 성공 시 Redis에서 삭제
        Long accountId = tokenService.validateAndConsumePasswordResetToken(request.getPasswordResetToken());

        // 3. 회원 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 4. OAuth 계정은 로컬 비밀번호 재설정을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 5. 비밀번호 변경
        account.changePassword(passwordEncoder.encode(request.getNewPassword()));

        // 6. 기존 Refresh Token 삭제
        // 비밀번호 변경 후 기존 로그인 유지 차단
        tokenService.deleteRefreshToken(accountId);

        // 7. Password Reset Token 삭제
        tokenService.deletePasswordResetToken(accountId);

        log.info("비밀번호 재설정 완료: accountId={}", accountId);
    }

    @Transactional
    //사용자가 입력한 이메일 인증 코드를 검증 메서드
    public String verifyresetPasswordEmailCode(EmailVerifyRequestDto request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 1. 인증코드 검증
        emailService.verifyPasswordResetCode(request.getEmail(), request.getCode());

        // 2. 검증 성공 후 JWT password reset token 발급
        return tokenService.generateAndSavePasswordResetToken(account.getAccountId());
    }

    // ===================== 재인증 =====================

    @Transactional
    public ReAuthResponseDto reAuth(Long accountId, ReAuthRequestDto request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountStatus(account);

        if (account.isLocalAccount()) {
            validateLocalReAuth(account, request);
        } else {
            validateOAuthReAuth(account, request);
        }

        String reAuthToken = tokenService.generateAndSaveReAuthToken(accountId);

        return ReAuthResponseDto.builder()
                .reAuthToken(reAuthToken)
                .expiresIn(jwtProvider.getReauthTokenExpiration())
                .build();
    }

    // ===================== 내부 유틸 =====================

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

        // TODO: oAuthClient.validateToken(account.getProvider(), request.getOauthToken());
    }

    private LocalDate parseOpeningDate(String openingDate) {
        try {
            return LocalDate.parse(
                    openingDate,
                    DateTimeFormatter.ofPattern("yyyyMMdd")
            );
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.COMMUNITY_COMMENT_ACCESS_DENIED);
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

    private TokenResponseDto issueTokens(Account account) {
        String accessToken = jwtProvider.generateAccessToken(
                account.getAccountId(),
                account.getRole().name()
        );

        String refreshToken = jwtProvider.generateRefreshToken(account.getAccountId());

        tokenService.saveRefreshToken(account.getAccountId(), refreshToken);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtProvider.getAccessTokenExpiration())
                .refreshTokenExpiresIn(jwtProvider.getRefreshTokenExpiration())
                .role(account.getRole().name())
                .build();
    }
}