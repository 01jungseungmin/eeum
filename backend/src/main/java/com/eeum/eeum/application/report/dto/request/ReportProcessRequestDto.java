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
            description = "처리 조치. 게시글=HIDE_POST/DELETE_POST/WARN_AUTHOR/SUSPEND_AUTHOR, " +
                    "댓글=DELETE_COMMENT/WARN_AUTHOR/SUSPEND_AUTHOR, " +
                    "리뷰=DELETE_STORE_REVIEW/WARN_AUTHOR/SUSPEND_AUTHOR, " +
                    "상점=SUSPEND_STORE/WARN_AUTHOR/SUSPEND_AUTHOR, " +
                    "회원=WARN_AUTHOR/SUSPEND_AUTHOR, 공통=DISMISS",
            example = "HIDE_POST",
            allowableValues = {
                    "HIDE_POST", "DELETE_POST", "DELETE_COMMENT", "DELETE_STORE_REVIEW",
                    "WARN_AUTHOR", "SUSPEND_AUTHOR", "SUSPEND_STORE", "DISMISS"
            }
    )
    private ReportAction action;

    @NotBlank
    @Size(max = 1000)
    @Schema(description = "관리자 처리 사유", example = "운영 정책을 위반한 게시글입니다.")
    private String adminNote;
}
