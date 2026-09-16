package com.eeum.eeum.domain.sanction.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionSource;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "sanction_history",
        indexes = {
                @Index(
                        name = "idx_sanction_history_target_created",
                        columnList = "target_type, target_id, created_at"
                ),
                @Index(name = "idx_sanction_history_admin", columnList = "processed_by_admin_id"),
                @Index(name = "idx_sanction_history_report", columnList = "source_report_id")
        }
)
public class SanctionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_history_id")
    private Long sanctionHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private SanctionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private SanctionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SanctionSource source;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "source_report_id")
    private Long sourceReportId;

    public static SanctionHistory record(
            SanctionTargetType targetType,
            Long targetId,
            SanctionAction action,
            SanctionSource source,
            String adminNote,
            Long processedByAdminId,
            Long sourceReportId
    ) {
        SanctionHistory history = new SanctionHistory();
        history.targetType = targetType;
        history.targetId = targetId;
        history.action = action;
        history.source = source;
        history.adminNote = adminNote;
        history.processedByAdminId = processedByAdminId;
        history.sourceReportId = sourceReportId;
        return history;
    }
}
