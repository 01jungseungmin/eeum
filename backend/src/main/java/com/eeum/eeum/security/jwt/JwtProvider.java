package com.eeum.eeum.security.jwt;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// 토큰 생성/검증/파싱 담당(토큰 문자열을 만들고 해석하는 클래스)
@Slf4j //로그
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type"; //type == 토큰 종류(ACCESS,REFRESH,REAUTH,PASSWORD_RESET)
    private static final String ROLE_CLAIM = "role"; //role == 회원의 role(ROLE_USER,ROLE_OWNER,ROLE_ADMIN)

    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String TOKEN_TYPE_REAUTH = "REAUTH";
    private static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    private final SecretKey secretKey; //JWT 서명에 사용할 비밀키(사용자가 내용을 조작하지 못하게하는 서명(signature))

    private static final String BEARER_PREFIX = "Bearer ";


    //각 토큰의 만료시간(application.yml에 작성)

    // Access Token 만료 시간(초) - API 인증 토큰의 유효 시간
    @Getter
    private final long accessTokenExpiration;

    // Refresh Token 만료 시간(초) - Access Token 재발급 및 Redis TTL 설정에 사용
    @Getter
    private final long refreshTokenExpiration;

    // 재인증 토큰 만료 시간(초) - 비밀번호 변경, 회원 탈퇴 등 민감 작업 검증에 사용
    @Getter
    private final long reauthTokenExpiration;

    // 비밀번호 재설정 토큰 만료 시간(초) - 비밀번호 재설정 링크 및 Redis TTL 설정에 사용
    @Getter
    private final long passwordResetTokenExpiration;

    //application.yml에 있는 JWT 설정값을 주입
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.reauth-token-expiration}") long reauthTokenExpiration,
            @Value("${jwt.password-reset-token-expiration}") long passwordResetTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.reauthTokenExpiration = reauthTokenExpiration;
        this.passwordResetTokenExpiration = passwordResetTokenExpiration;
    }

    // ===================== 토큰 생성 =====================

    public String generateAccessToken(Long accountId, String role) { //Access Token을 생성
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_ACCESS, role, accessTokenExpiration);
    }

    public String generateRefreshToken(Long accountId) { //Refresh Token 생성
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_REFRESH, null, refreshTokenExpiration);
    }

    public String generateReAuthToken(Long accountId) { //ReAuth Token 생성
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_REAUTH, null, reauthTokenExpiration);
    }

    public String generatePasswordResetToken(Long accountId) { //Password Reset Token 생성
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_PASSWORD_RESET, null, passwordResetTokenExpiration);
    }

    //JWT를 만드는 공통 메서드
    private String buildToken(String subject, String tokenType, String role, long expirationSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L); //만료시간 초 단위라서 밀리초로 바꾸기 위해 1000L을 곱

        JwtBuilder builder = Jwts.builder()
                .subject(subject) //JWT subject, 여기서는 accountId 문자열
                .claim(TOKEN_TYPE_CLAIM, tokenType) //ACCESS / REFRESH / REAUTH / PASSWORD_RESET
                .issuedAt(now) //발급시간
                .expiration(expiry) //만료시간
                .signWith(secretKey); //비밀키로 서명

        if (role != null) { //role이 있으면 JWT에 role claim을 추가 (Access Token에만 ROLE 값 존재)
            builder.claim(ROLE_CLAIM, role);
        }

        return builder.compact(); //JWT 구조 == Header.Payload.Signature
    }

    // ===================== 토큰 파싱 =====================

    public Long getAccountId(String token) { //토큰에서 accountId를 꺼내는 메서드
        try {
            return Long.parseLong(getClaims(token).getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("JWT subject가 올바른 accountId 형식이 아닙니다.", e);
        }
    }

    public String getRole(String token) { //토큰에서 role 값을 꺼내는 메서드
        return getClaims(token).get(ROLE_CLAIM, String.class);
    }

    public String getTokenType(String token) { //토큰의 타입을 꺼내는 메서드(토큰 용도 구분)
        return getClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    }

    public Date getExpiration(String token) { //토큰의 만료 시간을 꺼내는 메서드
        return getClaims(token).getExpiration();
    }

    public long getRemainingSeconds(String token) { //토큰이 앞으로 몇 초 남았는지 계산하는 메서드
        long remainingMillis = getExpiration(token).getTime() - System.currentTimeMillis();
        return Math.max(remainingMillis / 1000L, 0L);
    }

    // ===================== 토큰 검증 =====================

    public boolean isValid(String token) { //JWT가 유효한지 검사하는 메서드
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT 토큰: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) { //토큰의 타입이 ACCESS인지 확인하는 메서드
        return hasTokenType(token, TOKEN_TYPE_ACCESS);
    }

    public boolean isRefreshToken(String token) { //토큰의 타입이 REFRESH인지 확인하는 메서드
        return hasTokenType(token, TOKEN_TYPE_REFRESH);
    }

    public boolean isReAuthToken(String token) { //토큰의 타입이 REAUTH인지 확인하는 메서드
        return hasTokenType(token, TOKEN_TYPE_REAUTH);
    }

    public boolean isPasswordResetToken(String token) { //토큰의 타입이 RESET인지 확인하는 메서드
        return hasTokenType(token, TOKEN_TYPE_PASSWORD_RESET);
    }

    private boolean hasTokenType(String token, String expectedType) { //토큰 타입 검사 공통 메서드
        try {
            return expectedType.equals(getTokenType(token));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ===================== 내부 유틸 =====================

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey) //이 비밀키로 서명을 검증
                .build() //파서 완성
                .parseSignedClaims(token) //서명된 JWT를 파싱(이때 문제가 있으면 예외가 발생)
                .getPayload(); //토큰의 payload, 즉 claims를 꺼냄
    }

    public String resolveAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        if (!isValid(token) || !isAccessToken(token)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        return token;
    }

}