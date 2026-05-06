package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.EmailSendRequestDto;
import com.eeum.eeum.application.auth.dto.request.EmailVerifyRequestDto;
import com.eeum.eeum.application.auth.dto.request.LoginRequestDto;
import com.eeum.eeum.application.auth.dto.request.OAuthLoginRequestDto;
import com.eeum.eeum.application.auth.dto.request.OwnerSignupRequestDto;
import com.eeum.eeum.application.auth.dto.request.PasswordNewRequestDto;
import com.eeum.eeum.application.auth.dto.request.PasswordResetRequestDto;
import com.eeum.eeum.application.auth.dto.request.ReAuthRequestDto;
import com.eeum.eeum.application.auth.dto.request.ReissueRequestDto;
import com.eeum.eeum.application.auth.dto.request.SignupRequestDto;
import com.eeum.eeum.application.auth.dto.response.ReAuthResponseDto;
import com.eeum.eeum.application.auth.dto.response.TokenResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final EmailService emailService;

    // TODO: private final BusinessNumberClient businessNumberClient;(국세청 사업자번호 검증 API를 연동할 때 사용할 예정)
    // TODO: private final OAuthClient oAuthClient;(카카오/네이버 OAuth 로그인 검증을 담당할 클라이언트를 추가할 예정)

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
        // 1. 이메일 인증 토큰 검증 → 이메일 추출 (1회성 소비)
        String verifiedEmail = emailService.validateAndConsumeVerificationToken(
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

        log.info("일반 회원가입 완료: accountId={}", account.getAccountId());
    }

    @Transactional
    public void ownerSignup(OwnerSignupRequestDto request) {
        // 1. 이메일 인증 토큰 검증 → 이메일 추출 (1회성 소비)
        String verifiedEmail = emailService.validateAndConsumeVerificationToken(
                request.getEmailVerificationToken()
        );

        // 2. 요청 이메일과 인증된 이메일 일치 확인
        if (!verifiedEmail.equals(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        // 3. 중복 확인
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_EMAIL);
        }

        if (accountRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_NICKNAME);
        }

        if (ownerInfoRepository.existsByBusinessNumber(request.getBusinessNumber())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE_BUSINESS_NUMBER);
        }

        // 4. 국세청 사업자번호 API 검증
        // TODO: businessNumberClient.validate(request.getBusinessNumber());

        // 5. 계정 생성
        // 사장 회원가입 신청 시점에는 ROLE_USER로 생성하고, 관리자 승인 후 ROLE_OWNER로 변경
        Account account = Account.createOwner(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getNickname(),
                request.getPhone()
        );

        accountRepository.save(account);

        // 6. 사장 정보 등록
        // OwnerInfo의 approvalStatus는 create 내부에서 PENDING으로 설정하는 구조를 가정
        OwnerInfo ownerInfo = OwnerInfo.create(
                account,
                request.getPhone(),
                request.getBusinessNumber()
        );

        ownerInfoRepository.save(ownerInfo);

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

    @Transactional
    public TokenResponseDto oauthLogin(OAuthLoginRequestDto request) {
        // TODO: OAuthClient로 카카오/네이버 사용자 정보 조회 후 아래 흐름으로 대체
        // OAuthUserInfo userInfo = oAuthClient.getUserInfo(request.getProvider(), request.getCode());
        //
        // Account account = accountRepository
        //         .findByProviderAndProviderId(request.getProvider(), userInfo.getId())
        //         .orElseGet(() -> accountRepository.save(Account.createOAuthUser(...)));
        //
        // validateAccountStatus(account);
        //
        // return issueTokens(account);

        throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
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

        // 2. Access Token 자체 검증
        if (!jwtProvider.isValid(accessToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

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

        // Password Reset Token 생성 + Redis 저장
        String resetToken = tokenService.generateAndSavePasswordResetToken(account.getAccountId());

        // 비밀번호 재설정 이메일 발송
        emailService.sendPasswordResetEmail(account.getEmail(), resetToken);
    }

    @Transactional
    public void resetPassword(PasswordNewRequestDto request) {
        // 1. Password Reset Token 검증
        // - JWT 유효성
        // - type == PASSWORD_RESET
        // - Redis 저장값과 일치 여부 확인
        // - 검증 성공 시 Redis에서 삭제
        Long accountId = tokenService.validateAndConsumePasswordResetToken(request.getToken());

        // 2. 회원 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 3. OAuth 계정은 로컬 비밀번호 재설정을 허용하지 않음
        if (account.isOAuthAccount()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        // 4. 비밀번호 변경
        account.changePassword(passwordEncoder.encode(request.getNewPassword()));

        // 5. 기존 Refresh Token 삭제
        // 비밀번호 변경 후 기존 로그인 유지 차단
        tokenService.deleteRefreshToken(accountId);

        log.info("비밀번호 재설정 완료: accountId={}", accountId);
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