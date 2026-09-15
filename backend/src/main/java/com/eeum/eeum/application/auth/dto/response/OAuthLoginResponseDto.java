package com.eeum.eeum.application.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "OAuth 로그인 응답")
//OAuth 로그인 요청 결과
public class OAuthLoginResponseDto {

    @Schema(description = "신규 회원 여부")
    private boolean isNewUser;

    @Schema(description = "추가 정보 입력 필요 여부")
    private boolean signupRequired;

    @Schema(description = "임시 토큰, 신규 회원만 사용")
    private String tempToken;

    @Schema(description = "Access Token, 기존 회원 또는 가입 완료 시 사용")
    private String accessToken;

    @Schema(description = "Refresh Token, 기존 회원 또는 가입 완료 시 사용")
    private String refreshToken;

    @Schema(description = "Access Token 만료 시간")
    private Long accessTokenExpiresIn;

    @Schema(description = "Refresh Token 만료 시간")
    private Long refreshTokenExpiresIn;

    @Schema(description = "회원 역할")
    private String role;

    @Schema(description = "사장 AI 구독 플랜. 사장이 아닌 회원은 null", example = "BASIC", allowableValues = {"FREE", "BASIC", "PRO"})
    private String aiPlanType;

    public static OAuthLoginResponseDto existingUser(TokenResponseDto tokens) {
        return OAuthLoginResponseDto.builder()
                .isNewUser(false)
                .signupRequired(false)
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .accessTokenExpiresIn(tokens.getAccessTokenExpiresIn())
                .refreshTokenExpiresIn(tokens.getRefreshTokenExpiresIn())
                .role(tokens.getRole())
                .aiPlanType(tokens.getAiPlanType())
                .build();
    }

    public static OAuthLoginResponseDto newUser(String tempToken) {
        return OAuthLoginResponseDto.builder()
                .isNewUser(true)
                .signupRequired(true)
                .tempToken(tempToken)
                .build();
    }
}
