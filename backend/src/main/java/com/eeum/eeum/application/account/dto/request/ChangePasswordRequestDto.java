package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
@Schema(description = "비밀번호 변경 요청")
public class ChangePasswordRequestDto {

    @Schema(description = "재인증 토큰", example = "reauth_token_abc123")
    @NotBlank(message = "재인증 토큰은 필수입니다")
    private String reAuthToken;

    @Schema(description = "현재 비밀번호", example = "OldPassword1!")
    @NotBlank(message = "현재 비밀번호는 필수입니다")
    private String currentPassword;

    @Schema(description = "새 비밀번호 (영문+숫자+특수문자 8자 이상)", example = "NewPassword1!")
    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8자 이상이어야 합니다"
    )
    private String newPassword;
}