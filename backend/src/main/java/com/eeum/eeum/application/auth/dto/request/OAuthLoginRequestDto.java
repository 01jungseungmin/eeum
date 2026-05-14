package com.eeum.eeum.application.auth.dto.request;

import com.eeum.eeum.domain.account.enums.OAuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "OAuth 로그인 요청")
public class OAuthLoginRequestDto {

    @NotNull(message = "OAuth 제공자는 필수입니다")
    @Schema(description = "OAuth 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER"})
    private OAuthProvider provider;

    @NotBlank(message = "Access Token은 필수입니다")
    @Schema(description = "카카오/네이버 SDK에서 발급받은 Access Token")
    private String accessToken;
}
