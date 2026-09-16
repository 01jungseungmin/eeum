package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "Access Token 재발급 요청")
public class ReissueRequestDto {

    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "Refresh Token은 필수입니다")
    private String refreshToken;
}