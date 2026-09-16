package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "사장 승인 추가 정보 입력 응답")
public class OwnerApprovalInfoResponseDto {

    @Schema(description = "사장 승인 상태", example = "PENDING")
    private String approvalStatus;

    @Schema(description = "화면 표시 상태", example = "NEED_INPUT")
    private String viewStatus;

    @Schema(description = "사업자 인증 완료 여부", example = "true")
    private boolean businessVerified;

    @Schema(description = "대표자 신원 확인 완료 여부", example = "true")
    private boolean identityVerified;

    @Schema(description = "상점 업종 선택 완료 여부", example = "true")
    private boolean storeCategorySelected;

    @Schema(description = "대표 메뉴 등록 완료 여부", example = "true")
    private boolean representativeMenuRegistered;

    @Schema(description = "영업시간 설정 완료 여부", example = "true")
    private boolean businessHoursSet;

    @Schema(description = "정산 계좌 등록 완료 여부", example = "true")
    private boolean settlementAccountRegistered;

    @Schema(description = "전체 필수 항목 완료 여부", example = "false")
    private boolean allCompleted;
}