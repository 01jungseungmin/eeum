package com.eeum.eeum.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 은행명이 Bank enum의 표시명 또는 별칭인지 검증한다.
 *
 * 자유 문자열을 그대로 받으면 같은 은행이 여러 표기로 저장돼 정산 대사에서 갈라진다.
 * 빈 값은 @NotBlank가 따로 판정하므로 여기서는 통과시킨다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedBankValidator.class)
public @interface SupportedBank {

    String message() default "지원하지 않는 은행입니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
