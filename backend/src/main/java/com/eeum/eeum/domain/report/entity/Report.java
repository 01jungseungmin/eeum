package com.eeum.eeum.domain.report.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_reporter_target",
                        columnNames = {"account_id", "target_type", "target_id"}
                )
        },
        indexes = {
                @Index(name = "idx_report_reporter", columnList = "account_id"),
                @Index(name = "idx_report_status", columnList = "status"),
                @Index(name = "idx_report_target", columnList = "target_type, target_id")
        }
)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ReportTargetType targetType;

    // Polymorphic 참조 — FK 없이 ID만 보관 (Favorite, ChatRoom 동일 패턴)
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "content", length = 1000)
    private String content;

    // 신고 접수 시점의 대상 콘텐츠. Polymorphic 대상이 수정/삭제돼도 관리자 검토 근거를 보존한다.
    @Column(name = "target_title_snapshot", length = 255)
    private String targetTitleSnapshot;

    @Column(name = "target_content_snapshot", columnDefinition = "TEXT")
    private String targetContentSnapshot;

    @Column(name = "target_owner_account_id_snapshot")
    private Long targetOwnerAccountIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    // 관리자 검토 메모 (선택)
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_action", length = 30)
    private ReportAction action;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "action_target_account_id")
    private Long actionTargetAccountId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ===================== 정적 팩토리 메서드 =====================

    public static Report create(
            Account reporter,
            ReportTargetType targetType,
            Long targetId,
            ReportReason reason,
            String content
    ) {
        return create(reporter, targetType, targetId, reason, content, null, null);
    }

    public static Report create(
            Account reporter,
            ReportTargetType targetType,
            Long targetId,
            ReportReason reason,
            String content,
            String targetTitleSnapshot,
            String targetContentSnapshot
    ) {
        return create(
                reporter,
                targetType,
                targetId,
                reason,
                content,
                targetTitleSnapshot,
                targetContentSnapshot,
                null
        );
    }

    public static Report create(
            Account reporter,
            ReportTargetType targetType,
            Long targetId,
            ReportReason reason,
            String content,
            String targetTitleSnapshot,
            String targetContentSnapshot,
            Long targetOwnerAccountIdSnapshot
    ) {
        Report report = new Report();
        report.reporter = reporter;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.content = content;
        report.targetTitleSnapshot = targetTitleSnapshot;
        report.targetContentSnapshot = targetContentSnapshot;
        report.targetOwnerAccountIdSnapshot = targetOwnerAccountIdSnapshot;
        report.status = ReportStatus.PENDING;
        return report;
    }

    // ===================== 도메인 메서드 =====================

    public void review(String adminNote) {
        review(null, adminNote);
    }

    public void review(Long adminId, String adminNote) {
        validatePending();
        this.status = ReportStatus.REVIEWED;
        this.adminNote = adminNote;
        this.processedByAdminId = adminId;
    }

    public void dismiss(String adminNote) {
        dismiss(null, adminNote);
    }

    public void dismiss(Long adminId, String adminNote) {
        validatePending();
        this.status = ReportStatus.DISMISSED;
        this.adminNote = adminNote;
        this.action = ReportAction.DISMISS;
        this.processedByAdminId = adminId;
    }

    public void process(
            ReportAction action,
            String adminNote,
            Long adminId,
            Long actionTargetAccountId
    ) {
        validatePending();
        this.status = action == ReportAction.DISMISS
                ? ReportStatus.DISMISSED
                : ReportStatus.REVIEWED;
        this.action = action;
        this.adminNote = adminNote;
        this.processedByAdminId = adminId;
        this.actionTargetAccountId = actionTargetAccountId;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.reporter.getAccountId().equals(accountId);
    }

    public void validateProcessable() {
        validatePending();
    }

    private void validatePending() {
        if (this.status != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }
}
