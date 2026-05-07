package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "FCM 토큰 등록 요청")
public class FcmTokenRequestDto {

    @Schema(description = "Firebase Cloud Messaging 토큰", example = "dGhpcyBpcyBhIHNhbXBsZSBmY20gdG9rZW4...")
    @NotBlank(message = "FCM 토큰은 필수입니다.")
    private String fcmToken;
}