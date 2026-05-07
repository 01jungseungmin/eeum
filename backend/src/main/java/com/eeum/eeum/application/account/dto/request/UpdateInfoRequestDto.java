package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "회원 정보 수정 요청")
public class UpdateInfoRequestDto {

    @Schema(description = "닉네임", example = "새닉네임")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자 이내여야 합니다")
    private String nickname;

    @Schema(description = "프로필 이미지 URL", example = "https://s3.amazonaws.com/...")
    private String profileImageUrl;
}
