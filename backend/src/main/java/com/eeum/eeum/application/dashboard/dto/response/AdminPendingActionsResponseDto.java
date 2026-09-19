package com.eeum.eeum.application.dashboard.dto.response;

import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.report.enums.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@Schema(description = "관리자 대시보드 처리 대기 항목")
public class AdminPendingActionsResponseDto {

    @Schema(description = "사장 가입 승인 대기")
    private final OwnerApprovals ownerApprovals;

    @Schema(description = "미처리 신고")
    private final Reports reports;

    @Schema(description = "미답변 관리자 문의")
    private final Inquiries inquiries;

    @Getter
    @Builder
    @Schema(description = "사장 가입 승인 대기 (심사 요청된 건만)")
    public static class OwnerApprovals {

        @Schema(description = "승인 대기 건수", example = "12")
        private final long count;

        @Schema(description = "가장 최근 심사 요청 상점명. 대기 건이 없으면 null", example = "수제 떡케이크 공방", nullable = true)
        private final String latestStoreName;

        @Schema(description = "가장 최근 심사 요청 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime latestRequestedAt;

        @Schema(description = "가장 오래 기다린 건의 심사 요청 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime oldestRequestedAt;
    }

    @Getter
    @Builder
    @Schema(description = "미처리(PENDING) 신고")
    public static class Reports {

        @Schema(description = "미처리 신고 건수", example = "5")
        private final long count;

        @Schema(description = "사유별 건수. 발생하지 않은 사유도 0으로 포함")
        private final Map<ReportReason, Long> countByReason;

        @Schema(description = "가장 최근 신고 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime latestReportedAt;

        @Schema(description = "가장 오래 기다린 신고 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime oldestReportedAt;
    }

    @Getter
    @Builder
    @Schema(description = "미답변(PENDING) 관리자 문의")
    public static class Inquiries {

        @Schema(description = "미답변 문의 건수", example = "6")
        private final long count;

        @Schema(description = "유형별 건수. 발생하지 않은 유형도 0으로 포함")
        private final Map<InquiryCategory, Long> countByCategory;

        @Schema(description = "가장 최근 문의 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime latestCreatedAt;

        @Schema(description = "가장 오래 기다린 문의 시각. 대기 건이 없으면 null", nullable = true)
        private final LocalDateTime oldestCreatedAt;
    }
}
