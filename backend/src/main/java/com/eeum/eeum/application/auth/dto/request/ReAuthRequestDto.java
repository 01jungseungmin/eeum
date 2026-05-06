package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "재인증 요청 (민감 작업 전)")
public class ReAuthRequestDto {

    @Schema(description = "비밀번호 (LOCAL 계정)", example = "Password1!")
    private String password;

    @Schema(description = "OAuth 토큰 (소셜 계정)", example = "oauth_access_token")
    private String oauthToken;
}
