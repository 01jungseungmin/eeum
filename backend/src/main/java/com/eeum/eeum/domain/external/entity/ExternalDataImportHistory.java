package com.eeum.eeum.domain.external.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.external.enums.ExternalImportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 공공데이터 파일 Import 이력 — 데이터 최신화 일시/기준을 추적
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_data_import_history")
public class ExternalDataImportHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_data_import_history_id")
    private Long externalDataImportHistoryId;

    @Column(name = "data_name", nullable = false, length = 100)
    private String dataName;

    // data.go.kr 데이터 ID (예: 15101311)
    @Column(name = "source_id", nullable = false, length = 20)
    private String sourceId;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    // 기관이 공개한 데이터 기준일 (파일 메타/관리자 입력)
    @Column(name = "source_updated_at")
    private LocalDate sourceUpdatedAt;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExternalImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public static ExternalDataImportHistory record(
            String dataName, String sourceId, String fileName, LocalDate sourceUpdatedAt,
            ExternalImportStatus status, int totalRows, int successRows, int failedRows, String failureReason
    ) {
        ExternalDataImportHistory history = new ExternalDataImportHistory();
        history.dataName = dataName;
        history.sourceId = sourceId;
        history.fileName = fileName;
        history.sourceUpdatedAt = sourceUpdatedAt;
        history.importedAt = LocalDateTime.now();
        history.status = status;
        history.totalRows = totalRows;
        history.successRows = successRows;
        history.failedRows = failedRows;
        history.failureReason = failureReason;
        return history;
    }
}
