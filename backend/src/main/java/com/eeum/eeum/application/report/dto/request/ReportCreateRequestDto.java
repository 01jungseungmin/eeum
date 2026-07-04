package com.eeum.eeum.application.report.dto.request;

import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "신고 작성 요청")
public class ReportCreateRequestDto {

    @NotNull
    @Schema(description = "신고 대상 유형", example = "STORE")
    private ReportTargetType targetType;

    @NotNull
    @Schema(description = "신고 대상 ID", example = "1")
    private Long targetId;

    @NotNull
    @Schema(description = "신고 사유", example = "FALSE_INFORMATION")
    private ReportReason reason;

    @Size(max = 1000)
    @Schema(description = "신고 상세 내용 (선택)")
    private String content;
}
