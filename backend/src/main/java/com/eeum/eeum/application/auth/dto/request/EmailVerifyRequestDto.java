package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
@Schema(description = "이메일 인증 코드 검증 요청")
public class EmailVerifyRequestDto {

    @Schema(description = "이메일", example = "user@example.com")
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    @Schema(description = "인증 코드 (6자리)", example = "123456")
    @NotBlank(message = "인증 코드는 필수입니다")
    @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 숫자 6자리여야 합니다")
    private String code;
}

