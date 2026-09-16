package com.eeum.eeum.application.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "재인증 응답")
public class ReAuthResponseDto {

    @Schema(description = "재인증 토큰 (TTL 5분, 1회성)", example = "reauth_token_abc123")
    private String reAuthToken;

    @Schema(description = "재인증 토큰 만료 시간 (초)", example = "300")
    private Long expiresIn;
}
