package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
@Schema(description = "비밀번호 재설정 요청")
public class PasswordNewRequestDto {

    @Schema(description = "재설정 토큰", example = "7f8e9d2a-1234-5678-abcd-ef0123456789")
    @NotBlank(message = "재설정 토큰은 필수입니다")
    private String token;

    @Schema(description = "새 비밀번호 (영문+숫자+특수문자 8자 이상)", example = "NewPassword1!")
    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8자 이상이어야 합니다"
    )
    private String newPassword;
}