package com.eeum.eeum.application.report.dto.response;

import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "내 신고 응답")
public class MyReportResponseDto {

    private final Long reportId;
    private final ReportTargetType targetType;
    private final Long targetId;
    private final ReportReason reason;
    private final String content;
    private final Long reporterId;
    private final String reporterName;
    private final ReportStatus status;
    private final ReportAction action;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime reportedAt;
    private final LocalDateTime processedAt;

    public static MyReportResponseDto from(Report report) {
        boolean processed = report.getStatus() != ReportStatus.PENDING;
        return MyReportResponseDto.builder()
                .reportId(report.getReportId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .content(report.getContent())
                .reporterId(report.getReporter().getAccountId())
                .reporterName(report.getReporter().getName())
                .status(report.getStatus())
                .action(report.getAction())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .reportedAt(report.getCreatedAt())
                .processedAt(processed ? report.getModifiedAt() : null)
                .build();
    }
}
