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

    @Schema(description = "S3 업로드 확정 objectKey (기존 HTTPS URL은 전환 기간 동안 허용)", example = "profiles/42/550e8400-e29b-41d4-a716-446655440000.webp")
    private String profileImageUrl;
}
