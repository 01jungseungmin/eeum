package com.eeum.eeum.application.report.dto.response;

import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "신고 응답")
public class ReportResponseDto {

    @Schema(description = "신고 ID")
    private Long reportId;

    @Schema(description = "신고자 ID")
    private Long reporterId;

    @Schema(description = "신고자 이름")
    private String reporterName;

    @Schema(description = "신고 대상 유형")
    private ReportTargetType targetType;

    @Schema(description = "신고 대상 ID")
    private Long targetId;

    @Schema(description = "신고 사유")
    private ReportReason reason;

    @Schema(description = "신고 상세 내용")
    private String content;

    @Schema(description = "처리 상태")
    private ReportStatus status;

    @Schema(description = "관리자 메모")
    private String adminNote;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private LocalDateTime updatedAt;

    public static ReportResponseDto from(Report report) {
        return ReportResponseDto.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getAccountId())
                .reporterName(report.getReporter().getName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .content(report.getContent())
                .status(report.getStatus())
                .adminNote(report.getAdminNote())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .build();
    }
}
