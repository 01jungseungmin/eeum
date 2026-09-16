package com.eeum.eeum.application.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "신고 검토 요청")
public class ReportReviewRequestDto {

    @Size(max = 1000)
    @Schema(description = "관리자 메모 (선택),1000자 제한", example = "검토 완료")
    private String adminNote;
}
