package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "회원 탈퇴 요청")
public class WithdrawRequestDto {

    @Schema(description = "재인증 토큰", example = "reauth_token_abc123")
    @NotBlank(message = "재인증 토큰은 필수입니다")
    private String reAuthToken;
}