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

    @Schema(description = "S3 업로드 확정 objectKey. 기존 이미지를 유지하려면 생략합니다. 조회 응답의 서명 URL은 저장할 수 없으며, 기존 일반 HTTPS URL만 전환 기간 동안 허용합니다.", example = "profiles/42/550e8400-e29b-41d4-a716-446655440000.webp")
    private String profileImageUrl;
}
