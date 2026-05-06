package com.eeum.eeum.application.auth.dto.request;

import com.eeum.eeum.domain.account.enums.OAuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "OAuth 로그인 요청")
public class OAuthLoginRequestDto {

    @Schema(description = "OAuth 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER"})
    @NotNull(message = "OAuth 제공자는 필수입니다")
    private OAuthProvider provider;

    @Schema(description = "OAuth 인가 코드", example = "abc123def456")
    @NotBlank(message = "인가 코드는 필수입니다")
    private String code;
}
