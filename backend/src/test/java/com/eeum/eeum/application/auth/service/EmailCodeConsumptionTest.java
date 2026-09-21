package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이메일 인증코드 소비의 원자성 회귀.
 *
 * 값 비교와 삭제를 나눠 하면 같은 코드로 동시에 들어온 두 요청이 모두 통과하고,
 * 그 사이 재발송된 새 코드를 옛 요청이 지운다.
 */
@ExtendWith(MockitoExtension.class)
class EmailCodeConsumptionTest {

    @InjectMocks EmailService emailService;
    @Mock JavaMailSender mailSender;
    @Mock RedisUtil redisUtil;
    @Mock RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "codeExpiration", 300L);
        ReflectionTestUtils.setField(emailService, "tokenExpiration", 1800L);
    }

    @Test
    void 이미_소비된_코드로는_인증_토큰을_받지_못한다() {
        // given — 읽을 때는 남아 있었지만 삭제 시점에는 다른 요청이 가져갔다
        when(redisUtil.get("email:code:a@test.com")).thenReturn(Optional.of("123456"));
        when(redisUtil.compareAndDelete("email:code:a@test.com", "123456")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> emailService.verifyCodeAndIssueToken("a@test.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);

        // 인증 토큰을 발급하지 않아야 한다
        verify(redisUtil, never()).set(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 비밀번호_재설정_코드도_한_번만_소비된다() {
        String key = "email:password-reset:code:a@test.com";
        when(redisUtil.get(key)).thenReturn(Optional.of("654321"));
        when(redisUtil.compareAndDelete(key, "654321")).thenReturn(false);

        assertThatThrownBy(() -> emailService.verifyPasswordResetCode("a@test.com", "654321"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);

        verify(rateLimitService, never()).resetFailure(anyString());
    }
}
