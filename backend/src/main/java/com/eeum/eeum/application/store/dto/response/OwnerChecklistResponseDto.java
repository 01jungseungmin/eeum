package com.eeum.eeum.application.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "사장 입점 심사 체크리스트 응답")
public class OwnerChecklistResponseDto {

    @Schema(description = "사업자 인증 완료 여부", example = "true")
    private boolean businessVerified;

    @Schema(description = "상점 기본 정보 입력 완료 여부", example = "true")
    private boolean storeInfoCompleted;

    @Schema(description = "대표 메뉴 1개 이상 등록 여부", example = "false")
    private boolean menuRegistered;

    @Schema(description = "영업시간 설정 완료 여부", example = "false")
    private boolean businessHoursSet;

    @Schema(description = "정산 계좌 등록 완료 여부", example = "false")
    private boolean settlementAccountRegistered;

    @Schema(description = "모든 항목 완료 여부 (심사 요청 버튼 활성화 기준)", example = "false")
    private boolean allCompleted;

    @Schema(description = "심사 요청 일시 (null이면 미신청)", example = "2026-05-06T12:00:00")
    private java.time.LocalDateTime reviewRequestedAt;

    @Schema(description = "승인 상태", example = "PENDING")
    private String approvalStatus;

    @Schema(description = "반려 사유", example = "정산 계좌 정보가 올바르지 않습니다.")
    private String rejectionReason;
}