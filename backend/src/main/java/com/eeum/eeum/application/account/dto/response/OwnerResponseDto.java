package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "사업자 정보 응답")
public class OwnerResponseDto {

    @Schema(description = "사업자 정보 ID", example = "1")
    private Long ownerInfoId;

    @Schema(description = "회원 ID", example = "1")
    private Long accountId;

    @Schema(description = "사업자번호 (마스킹)", example = "123-**-*****")
    private String businessNumber;

    @Schema(description = "승인 상태", example = "PENDING",
            allowableValues = {"PENDING", "APPROVED", "REJECTED"})
    private String approvalStatus;

    @Schema(description = "거절 사유 (REJECTED 시에만)", example = "서류 미비")
    private String rejectionReason;

    @Schema(description = "등록일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;
}