package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String EMAIL_CODE_PREFIX  = "email:code:";
    private static final String EMAIL_TOKEN_PREFIX = "email:token:";
    private static final String PASSWORD_RESET_CODE_PREFIX = "email:password-reset:code:";

    private final JavaMailSender mailSender;
    private final RedisUtil redisUtil;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${auth.email-verification-code-expiration}")
    private long codeExpiration;

    @Value("${auth.email-verification-token-expiration}")
    private long tokenExpiration;

    // ===================== 인증 코드 발송 =====================

    public void sendVerificationCode(String email) {
        String code = generateCode();
        redisUtil.set(EMAIL_CODE_PREFIX + email, code, codeExpiration);

        String subject = "[이음] 이메일 인증 코드";
        String content = buildCodeEmailContent(code);

        try{
            sendHtmlEmail(email, subject, content);
        }catch (BusinessException e){
            redisUtil.delete(EMAIL_CODE_PREFIX + email);
            throw e;
        }


        log.info("이메일 인증 코드 발송 완료: {}", email);
    }

    // 인증 코드 검증 후 1회성 인증 토큰 반환
    public String verifyCodeAndIssueToken(String email, String code) {
        String stored = redisUtil.get(EMAIL_CODE_PREFIX + email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EXPIRED_VERIFICATION_CODE));

        if (!stored.equals(code)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }

        // 코드 사용 후 삭제
        redisUtil.delete(EMAIL_CODE_PREFIX + email);

        // 인증 토큰 발급 (회원가입 요청 시 같이 보내는 값)
        String token = UUID.randomUUID().toString();
        redisUtil.set(EMAIL_TOKEN_PREFIX + token, email, tokenExpiration);

        return token;
    }

    //회원가입 시 인증 토큰 검증

    public String validateVerificationToken(String token) {
        String email = redisUtil.get(EMAIL_TOKEN_PREFIX + token)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));

        return email;
    }

    public void consumeVerificationToken(String token) {
        redisUtil.delete(EMAIL_TOKEN_PREFIX + token);
    }

    // ===================== 비밀번호 재설정 코드 발송 =====================

    public void sendPasswordResetEmail(String email) {
        String code = generateCode();
        redisUtil.set(PASSWORD_RESET_CODE_PREFIX  + email, code, codeExpiration);

        String subject = "[이음] 비밀번호 재설정 안내";
        String content = buildCodeEmailContent(code);
        try{
            sendHtmlEmail(email, subject, content);
        }catch (BusinessException e){
            redisUtil.delete(PASSWORD_RESET_CODE_PREFIX + email);
            throw e;
        }


        log.info("비밀번호 재설정 메일 발송 완료: {}", email);
    }

    // 인증 토큰 발급 (비밀번호 재설정 요청 시 같이 보내는 값)
    public void verifyPasswordResetCode(String email, String code) {
        String stored = redisUtil.get(PASSWORD_RESET_CODE_PREFIX + email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EXPIRED_VERIFICATION_CODE));

        if (!stored.equals(code)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }

        // 코드 사용 후 삭제
        redisUtil.delete(PASSWORD_RESET_CODE_PREFIX + email);
    }

    // ===================== 내부 유틸 =====================

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(code);
    }

    private void sendHtmlEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true = HTML
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("이메일 발송 실패: to={}, error={}", to, e.getMessage());
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR, "이메일 발송에 실패했습니다");
        }
    }

    private String buildCodeEmailContent(String code) {
        return """
                <div style="font-family: sans-serif; max-width: 480px; margin: 0 auto;">
                    <h2 style="color: #333;">이음 이메일 인증</h2>
                    <p>아래 인증 코드를 입력해 주세요.</p>
                    <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px;
                                color: #4A90E2; padding: 16px; background: #f5f5f5;
                                border-radius: 8px; text-align: center;">
                        %s
                    </div>
                    <p style="color: #888; font-size: 12px; margin-top: 16px;">
                        인증 코드는 5분 후 만료됩니다.<br>
                        본인이 요청하지 않은 경우 이 메일을 무시하세요.
                    </p>
                </div>
                """.formatted(code);
    }
}