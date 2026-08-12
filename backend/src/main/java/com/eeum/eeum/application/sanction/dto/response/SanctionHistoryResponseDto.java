package com.eeum.eeum.application.sanction.dto.response;

import com.eeum.eeum.domain.sanction.entity.SanctionHistory;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionSource;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "회원·상점 제재 이력")
public class SanctionHistoryResponseDto {

    private Long sanctionHistoryId;
    private SanctionTargetType targetType;
    private Long targetId;
    private SanctionAction action;
    private SanctionSource source;
    private String adminNote;
    private Long processedByAdminId;
    private Long sourceReportId;
    private LocalDateTime createdAt;

    public static SanctionHistoryResponseDto from(SanctionHistory history) {
        return SanctionHistoryResponseDto.builder()
                .sanctionHistoryId(history.getSanctionHistoryId())
                .targetType(history.getTargetType())
                .targetId(history.getTargetId())
                .action(history.getAction())
                .source(history.getSource())
                .adminNote(history.getAdminNote())
                .processedByAdminId(history.getProcessedByAdminId())
                .sourceReportId(history.getSourceReportId())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
