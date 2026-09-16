package com.eeum.eeum.application.auth.dto.response;

import com.eeum.eeum.domain.account.enums.OAuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Schema
@NoArgsConstructor
@AllArgsConstructor
@Builder
//외부 OAuth 데이터
public class OAuthUserInfo {

    private OAuthProvider provider;

    @Schema(description = "oauth_ID", example = "1")
    private String providerId;

    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    private String name;

    @Schema(description = "닉네임", example = "동네주민")
    private String nickname;

    @Schema(description = "프로필이미지", example = "/default-profile.png")
    private String profileImage;

}
