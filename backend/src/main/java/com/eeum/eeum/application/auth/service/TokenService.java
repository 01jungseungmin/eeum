package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.security.jwt.JwtProvider;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
//JWT 토큰을 Redis와 함께 관리하는 서비스(토큰을 서버가 기억해야 할 때 Redis에 저장하고 검증하는 클래스)
public class TokenService {

    // Redis 키 prefix
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:access:";
    private static final String REAUTH_TOKEN_PREFIX = "reauth:";
    private static final String PASSWORD_RESET_TOKEN_PREFIX = "password-reset:";

    private final JwtProvider jwtProvider;
    private final RedisUtil redisUtil;

    // ===================== Refresh Token =====================
    //Refresh Token 저장
    public void saveRefreshToken(Long accountId, String refreshToken) {
        redisUtil.set( //Redis에 값 저장
                refreshTokenKey(accountId),
                refreshToken, //Redis에 저장할 value
                jwtProvider.getRefreshTokenExpiration() //Redis TTL
        );
    }

    /**
     * Refresh Token 검증
     * 1. JWT 자체 유효성
     * 2. REFRESH 타입 확인
     * 3. Redis 저장값과 일치 여부 확인
     */
    //Refresh Token을 검증하고, 성공하면 accountId를 반환
    public Long validateRefreshToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) { //Refresh Token 자체 유효성 검사(JWT 형식,서명 맞는지, 만료 여부)
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        if (!jwtProvider.isRefreshToken(refreshToken)) { //REFRESH 타입 확인
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        Long accountId = jwtProvider.getAccountId(refreshToken); //Refresh Token 안에서 accountId 추출

        String storedToken = redisUtil.get(refreshTokenKey(accountId)) //Redis에서 저장된 Refresh Token을 조회
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN)); //Redis에 저장된 값이 없으면 예외

        if (!storedToken.equals(refreshToken)) { //Redis에 저장된 Refresh Token과 사용자가 보낸 Refresh Token이 같은지 비교
            // 저장된 토큰과 불일치 → 탈취 가능성, 저장된 토큰도 삭제
            deleteRefreshToken(accountId); //저장된 Refresh Token을 Redis에서 삭제
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN); //토큰 오류 예외
        }

        return accountId; //검증 성공 시 accountId를 반환
    }

    public void deleteRefreshToken(Long accountId) { //저장된 Refresh Token을 Redis에서 삭제
        redisUtil.delete(refreshTokenKey(accountId)); //예를 들어 accountId = 1 delete refresh:1
    }

    // ===================== Access Token 블랙리스트 =====================

    public void blacklistAccessToken(String accessToken) { //Access Token을 블랙리스트에 등록하는 메서드
        if (!jwtProvider.isValid(accessToken) || !jwtProvider.isAccessToken(accessToken)) { //Access Token이 아닌 토큰이 블랙리스트 등록 방지
            return;
        }

        long remainingSeconds = jwtProvider.getRemainingSeconds(accessToken); //Access Token이 만료되기까지 남은 시간을 초 단위로 계산

        if (remainingSeconds <= 0) { //아직 만료된 토큰 검사
            return;
        }

        redisUtil.set(accessTokenBlacklistKey(accessToken), "logout", remainingSeconds); //Access Token을 블랙리스트로 Redis에 저장
    }

    public boolean isBlacklisted(String accessToken) { //Access Token이 블랙리스트에 있는지 확인하는 메서드
        return redisUtil.hasKey(accessTokenBlacklistKey(accessToken)); //Redis에 해당 key가 있는지 확인 -> 존재 시 로그아웃된 Access Token
    }

    // ===================== ReAuth Token =====================
    //ReAuth Token 생성 및 저장
    public String generateAndSaveReAuthToken(Long accountId) {
        String reAuthToken = jwtProvider.generateReAuthToken(accountId); //wtProvider에게 ReAuth Token 생성을 요청

        redisUtil.set( //Redis에 저장 시작
                reAuthTokenKey(accountId), //Redis key
                reAuthToken, //Redis value
                jwtProvider.getReauthTokenExpiration() //TTL
        );

        return reAuthToken; //생성한 ReAuth Token을 반환
    }

    /**
     * ReAuth Token 검증 후 즉시 삭제한다.
     * 비밀번호 변경, 회원 탈퇴 등 민감 작업에서 사용한다.
     */
    //ReAuth Token을 검증하고, 성공하면 accountId를 반환
    public void validateAndConsumeReAuthToken(Long currentAccountId, String reAuthToken) {
        if (!jwtProvider.isValid(reAuthToken)) { //토큰 자체가 유효한지 검사
            throw new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN); //유효하지 않으면 재인증 토큰 오류
        }

        if (!jwtProvider.isReAuthToken(reAuthToken)) { //토큰 타입이 REAUTH인지 확인
            throw new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN); //ReAuth Token이 아니면 예외 발생
        }

        Long tokenAccountId = jwtProvider.getAccountId(reAuthToken); //ReAuth Token 안에서 accountId 추출

        if (!currentAccountId.equals(tokenAccountId)) { //현재 로그인한 사용자 ID와 재인증 토큰 안의 사용자 ID가 같은지 비교
            throw new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN); //현재 사용자와 토큰 주인이 다르면 예외
        }

        String storedToken = redisUtil.get(reAuthTokenKey(currentAccountId)) //Redis에서 현재 사용자에게 저장된 재인증 토큰을 조회
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN)); //Redis에 reauth:{accountId} key가 없으면 예외

        if (!storedToken.equals(reAuthToken)) { //Redis에 저장된 재인증 토큰과 사용자가 보낸 재인증 토큰이 같은지 비교
            throw new BusinessException(ErrorCode.AUTH_INVALID_REAUTH_TOKEN); //Redis 저장값과 요청 토큰이 다르면 예외
        }

        //Redis에서 재인증 토큰 삭제 (1회성 사용 처리)
        redisUtil.delete(reAuthTokenKey(currentAccountId));
    }

    // ===================== Password Reset Token =====================

    //비밀번호 재설정 토큰을 생성하고 Redis에 저장한 뒤, 생성한 토큰 문자열을 반환하는 메서드
    public String generateAndSavePasswordResetToken(Long accountId) {
        String resetToken = jwtProvider.generatePasswordResetToken(accountId); //JwtProvider를 이용해서 비밀번호 재설정용 JWT를 생성

        redisUtil.set( //Redis에 값을 저장하는 메서드
                passwordResetTokenKey(accountId), //key
                resetToken, //value
                jwtProvider.getPasswordResetTokenExpiration() //ttl
        );

        return resetToken; //재인증 토큰 반환
    }

    /**
     * Password Reset Token 검증 후 즉시 삭제한다.
     * 검증 성공 시 토큰의 accountId를 반환한다.
     */
    //비밀번호 재설정 토큰을 검증하는 메서드
    public Long validateAndConsumePasswordResetToken(String resetToken) {
        if (!jwtProvider.isValid(resetToken)) { //resetToken 자체가 유효한 JWT인지 검사
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN); //토큰 자체가 유효하지 않으면 비밀번호 재설정 토큰 오류
        }

        if (!jwtProvider.isPasswordResetToken(resetToken)) { //토큰 타입이 PASSWORD_RESET인지 확인
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN); //비밀번호 재설정 토큰 타입이 아니면 예외
        }

        Long accountId = jwtProvider.getAccountId(resetToken); //비밀번호 재설정 토큰 안에서 accountId 추출

        String storedToken = redisUtil.get(passwordResetTokenKey(accountId)) //Redis에서 해당 회원에게 저장된 비밀번호 재설정 토큰을 조회
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN)); //Redis에 저장된 비밀번호 재설정 토큰이 없으면 예외

        if (!storedToken.equals(resetToken)) { //Redis에 저장된 토큰과 사용자가 보낸 resetToken이 같은지 비교
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN); //Redis 저장값과 요청 토큰이 다르면 예외
        }

        //Redis에서 비밀번호 재설정 토큰 삭제 (1회성 사용 처리)
        redisUtil.delete(passwordResetTokenKey(accountId));

        return accountId;
    }

    // ===================== 로그아웃 =====================

    /**
     * 로그아웃 처리
     * 1. Access Token 블랙리스트 등록
     * 2. Refresh Token 삭제
     */
    //로그아웃 처리 메서드
    public void logout(Long accountId, String accessToken) {
        blacklistAccessToken(accessToken); //Access Token을 Redis 블랙리스트에 등록
        deleteRefreshToken(accountId); //Redis에 저장된 Refresh Token을 삭제
    }

    // ===================== Redis Key 생성 =====================

    private String refreshTokenKey(Long accountId) {
        return REFRESH_TOKEN_PREFIX + accountId;
    }

    private String reAuthTokenKey(Long accountId) {
        return REAUTH_TOKEN_PREFIX + accountId;
    }

    private String passwordResetTokenKey(Long accountId) {
        return PASSWORD_RESET_TOKEN_PREFIX + accountId;
    }

    private String accessTokenBlacklistKey(String accessToken) {
        return BLACKLIST_PREFIX + accessToken;
    }
}