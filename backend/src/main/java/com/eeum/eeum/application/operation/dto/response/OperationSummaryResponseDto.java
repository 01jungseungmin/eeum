package com.eeum.eeum.application.operation.dto.response;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@Schema(description = "운영 현황 요약 — 관리자 대시보드 상단 지표")
public class OperationSummaryResponseDto {

    @Schema(description = "미처리(PENDING) 신고 건수")
    private long pendingReportCount;

    @Schema(description = "미답변(PENDING) 관리자 문의 건수")
    private long pendingInquiryCount;

    @Schema(description = "집계 구간 내 전체 실패 건수")
    private long failureCount;

    @Schema(description = "집계 구간 내 카테고리별 실패 건수. 발생하지 않은 카테고리도 0으로 포함된다")
    private Map<OperationFailureCategory, Long> failureCountByCategory;

    @Schema(description = "최근 실패 이력 (최신순)")
    private List<OperationFailureLogResponseDto> recentFailures;

    @Schema(description = "실패 집계 기준 시각. 이 시각 이후 발생분만 집계됨")
    private LocalDateTime failureCountSince;
}
