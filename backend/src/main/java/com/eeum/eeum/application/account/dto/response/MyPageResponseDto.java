package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "마이페이지 응답")
public class MyPageResponseDto {

    @Schema(description = "회원 ID", example = "1")
    private Long accountId;

    @Schema(description = "이메일 (마스킹)", example = "us***@example.com")
    private String email;

    @Schema(description = "닉네임", example = "동네주민")
    private String nickname;

    @Schema(description = "이름 (마스킹)", example = "홍*동")
    private String name;

    @Schema(description = "프로필 이미지 URL", example = "https://s3.amazonaws.com/...")
    private String profileImageUrl;

    @Schema(description = "회원 역할", example = "ROLE_USER")
    private String role;

    @Schema(description = "계정 상태", example = "ACTIVE")
    private String status;

    @Schema(description = "OAuth 제공자", example = "LOCAL")
    private String provider;

    @Schema(description = "대표 활동 지역 ID", example = "1")
    private Long primaryRegionId;

    @Schema(description = "활동 지역 목록")
    private List<AccountRegionResponseDto> regions;

    @Schema(description = "가입일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;
}
