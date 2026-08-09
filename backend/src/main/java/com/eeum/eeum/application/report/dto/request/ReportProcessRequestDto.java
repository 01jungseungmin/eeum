package com.eeum.eeum.application.report.dto.request;

import com.eeum.eeum.domain.report.enums.ReportAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "신고 처리 조치 요청")
public class ReportProcessRequestDto {

    @NotNull
    @Schema(
            description = "처리 조치",
            example = "HIDE_POST",
            allowableValues = {"HIDE_POST", "DELETE_POST", "WARN_AUTHOR", "SUSPEND_AUTHOR", "DISMISS"}
    )
    private ReportAction action;

    @NotBlank
    @Size(max = 1000)
    @Schema(description = "관리자 처리 사유", example = "운영 정책을 위반한 게시글입니다.")
    private String adminNote;
}
