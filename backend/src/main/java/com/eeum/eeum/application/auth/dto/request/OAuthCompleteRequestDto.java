package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
//OAuth 신규 회원이 추가 정보 입력
public class OAuthCompleteRequestDto {
    @NotBlank(message = "임시 토큰은 필수입니다")
    @Schema(description = "OAuth 로그인 시 발급된 임시 토큰")
    private String tempToken;

    @NotBlank(message = "이름은 필수입니다")
    @Schema(description = "이름", example = "정승민")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다")
    @Schema(description = "전화번호", example = "01012345678")
    private String phone;

    @Schema(description = "닉네임, 입력하지 않으면 OAuth 닉네임 또는 랜덤 닉네임 사용", example = "동네주민")
    private String nickname;
}