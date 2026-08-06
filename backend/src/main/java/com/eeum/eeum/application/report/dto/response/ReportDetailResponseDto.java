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

/**
 * 관리자 신고 상세 응답.
 * 목록용 {@link ReportResponseDto}에 신고자 상세 / 대상 스냅샷 / 처리 내역을 추가한 형태.
 */
@Getter
@Builder
@Schema(description = "신고 상세 응답 (관리자)")
public class ReportDetailResponseDto {

    @Schema(description = "신고 ID")
    private final Long reportId;

    @Schema(description = "신고 대상 유형")
    private final ReportTargetType targetType;

    @Schema(description = "신고 대상 ID")
    private final Long targetId;

    @Schema(description = "신고 사유")
    private final ReportReason reason;

    @Schema(description = "신고 상세 내용")
    private final String content;

    @Schema(description = "신고자 ID")
    private final Long reporterId;

    @Schema(description = "신고자 이름")
    private final String reporterName;

    @Schema(description = "신고자 닉네임")
    private final String reporterNickname;

    @Schema(description = "신고자 이메일")
    private final String reporterEmail;

    @Schema(description = "신고 대상 상세 정보")
    private final ReportTargetSnapshotDto target;

    @Schema(description = "신고 접수 시각")
    private final LocalDateTime reportedAt;

    @Schema(description = "처리 상태")
    private final ReportStatus status;

    @Schema(description = "관리자 처리 메모 (미처리 시 null)")
    private final String adminNote;

    @Schema(description = "관리자 처리 시각 (PENDING이면 null)")
    private final LocalDateTime processedAt;

    public static ReportDetailResponseDto of(Report report, ReportTargetSnapshotDto target) {
        Account reporter = report.getReporter();
        boolean processed = report.getStatus() != ReportStatus.PENDING;
        return ReportDetailResponseDto.builder()
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
                .reportedAt(report.getCreatedAt())
                .status(report.getStatus())
                .adminNote(report.getAdminNote())
                // 처리 시각 전용 컬럼이 없어 상태 전이 시 갱신되는 modifiedAt을 사용 (PENDING이면 미처리)
                .processedAt(processed ? report.getModifiedAt() : null)
                .build();
    }
}
