package com.eeum.eeum.application.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "토큰 응답")
public class TokenResponseDto {

    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;

    @Schema(description = "Access Token 만료 시간 (초)", example = "1800")
    private Long accessTokenExpiresIn;

    @Schema(description = "Refresh Token 만료 시간 (초)", example = "1209600")
    private Long refreshTokenExpiresIn;

    @Schema(description = "회원 역할", example = "ROLE_USER", allowableValues = {"ROLE_USER", "ROLE_OWNER", "ROLE_ADMIN"})
    private String role;
}