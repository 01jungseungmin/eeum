package com.eeum.eeum.application.report.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
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

    @Schema(description = "신고 대상 유형")
    private ReportTargetType targetType;

    @Schema(description = "신고 대상 ID")
    private Long targetId;

    @Schema(description = "신고 사유")
    private ReportReason reason;

    @Schema(description = "신고 상세 내용")
    private String content;

    @Schema(description = "신고자 ID")
    private Long reporterId;

    @Schema(description = "신고자 이름")
    private final String reporterName;

    @Schema(description = "신고자 닉네임")
    private final String reporterNickname;

    @Schema(description = "신고자 이메일")
    private final String reporterEmail;

    @Schema(description = "신고 대상 상세 정보")
    private final ReportTargetSnapshotDto target;

    @Schema(description = "작성일시")
    private final LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private final LocalDateTime updatedAt;

    @Schema(description = "신고 접수 시각")
    private final LocalDateTime reportedAt;

    @Schema(description = "처리 상태")
    private ReportStatus status;

    @Schema(description = "관리자 메모")
    private String adminNote;

    @Schema(description = "관리자 처리 시각 (PENDING이면 null)")
    private final LocalDateTime processedAt;

    public static ReportResponseDto from(Report report) {
        boolean processed = report.getStatus() != ReportStatus.PENDING;
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
                .reportedAt(report.getCreatedAt())
                // 처리 시각 전용 컬럼이 없어 상태 전이 시 갱신되는 modifiedAt을 사용 (PENDING이면 미처리)
                .processedAt(processed ? report.getModifiedAt() : null)
                .build();
    }

    public static ReportResponseDto of(Report report, ReportTargetSnapshotDto target) {
        Account reporter = report.getReporter();
        boolean processed = report.getStatus() != ReportStatus.PENDING;
        return ReportResponseDto.builder()
                .reportId(report.getReportId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .content(report.getContent())
                .reporterId(reporter.getAccountId())
                .reporterName(reporter.getName())
                .reporterNickname(reporter.getNickname())
                .reporterEmail(reporter.getEmail())
                .target(target)
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .reportedAt(report.getCreatedAt())
                .status(report.getStatus())
                .adminNote(report.getAdminNote())
                // 처리 시각 전용 컬럼이 없어 상태 전이 시 갱신되는 modifiedAt을 사용 (PENDING이면 미처리)
                .processedAt(processed ? report.getModifiedAt() : null)
                .build();
    }
}
