package com.eeum.eeum.application.external.dto;

import com.eeum.eeum.domain.external.entity.ExternalDataImportHistory;
import com.eeum.eeum.domain.external.enums.ExternalImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "공공데이터 파일 Import 이력")
public class ExternalImportHistoryResponseDto {

    @Schema(description = "이력 ID")
    private final Long importHistoryId;

    @Schema(description = "데이터명", example = "한국전력공사_용도별 전력사용량")
    private final String dataName;

    @Schema(description = "data.go.kr 데이터 ID", example = "15101311")
    private final String sourceId;

    @Schema(description = "업로드 파일명")
    private final String fileName;

    @Schema(description = "기관 데이터 기준일")
    private final LocalDate sourceUpdatedAt;

    @Schema(description = "적재 일시")
    private final LocalDateTime importedAt;

    @Schema(description = "적재 상태", example = "SUCCESS")
    private final ExternalImportStatus status;

    @Schema(description = "전체 행 수")
    private final int totalRows;

    @Schema(description = "성공 행 수")
    private final int successRows;

    @Schema(description = "실패 행 수")
    private final int failedRows;

    @Schema(description = "실패 사유")
    private final String failureReason;

    public static ExternalImportHistoryResponseDto from(ExternalDataImportHistory history) {
        return ExternalImportHistoryResponseDto.builder()
                .importHistoryId(history.getExternalDataImportHistoryId())
                .dataName(history.getDataName())
                .sourceId(history.getSourceId())
                .fileName(history.getFileName())
                .sourceUpdatedAt(history.getSourceUpdatedAt())
                .importedAt(history.getImportedAt())
                .status(history.getStatus())
                .totalRows(history.getTotalRows())
                .successRows(history.getSuccessRows())
                .failedRows(history.getFailedRows())
                .failureReason(history.getFailureReason())
                .build();
    }
}
