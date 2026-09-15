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

@Slf4j //로그
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_VERSION_CLAIM = "ver";

    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String TOKEN_TYPE_REAUTH = "REAUTH";
    private static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    private final SecretKey secretKey;

    private static final String BEARER_PREFIX = "Bearer ";


    @Getter
    private final long accessTokenExpiration;

    @Getter
    private final long refreshTokenExpiration;

    @Getter
    private final long reauthTokenExpiration;

    @Getter
    private final long passwordResetTokenExpiration;

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

    // 모든 토큰에 발급 시점의 세대(ver)를 박는다. 제재 시 계정의 세대가 오르면
    // 그 이전에 발급된 토큰은 claim이 낡아 전부 무효가 된다(Account#tokenVersion 참고).
    public String generateAccessToken(Long accountId, String role, Long tokenVersion) {
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_ACCESS, role, tokenVersion, accessTokenExpiration);
    }

    public String generateRefreshToken(Long accountId, Long tokenVersion) {
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_REFRESH, null, tokenVersion, refreshTokenExpiration);
    }

    public String generateReAuthToken(Long accountId, Long tokenVersion) {
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_REAUTH, null, tokenVersion, reauthTokenExpiration);
    }

    public String generatePasswordResetToken(Long accountId, Long tokenVersion) {
        return buildToken(String.valueOf(accountId), TOKEN_TYPE_PASSWORD_RESET, null, tokenVersion, passwordResetTokenExpiration);
    }

    private String buildToken(String subject, String tokenType, String role,
                              Long tokenVersion, long expirationSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);

        if (role != null) {
            builder.claim(ROLE_CLAIM, role);
        }

        if (tokenVersion != null) {
            builder.claim(TOKEN_VERSION_CLAIM, tokenVersion);
        }

        return builder.compact();
    }

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

    /**
     * 토큰이 실린 세대. 계정의 현재 세대와 대조해 회수된 토큰을 걸러낸다.
     *
     * 이 기능 도입 전에 발급된 토큰에는 claim이 없어 null이 나온다.
     * 호출부는 그것을 무효로 다룬다(Account#isTokenVersionCurrent).
     */
    public Long getTokenVersion(String token) {
        Object claim = getClaims(token).get(TOKEN_VERSION_CLAIM);
        return claim instanceof Number number ? number.longValue() : null;
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
