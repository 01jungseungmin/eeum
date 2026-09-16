package com.eeum.eeum.application.account.dto.request;

import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "관리자 사장 신청 검색 조건")
public class OwnerInfoSearchDto {

    @Schema(description = "승인 상태", example = "PENDING")
    private ApprovalStatus approvalStatus;

    @Schema(description = "사업자번호 검색어", example = "123")
    private String businessNumber;

    @Schema(description = "상호명 검색어", example = "승민")
    private String storeName;

    @Schema(description = "신청 시작일", example = "2026-05-01")
    private LocalDate requestedFrom;

    @Schema(description = "신청 종료일", example = "2026-05-31")
    private LocalDate requestedTo;
}