package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "사장 승인 거절 요청")
public class RejectRequestDto {

    @Schema(description = "거절 사유", example = "서류 미비 및 사업자 정보 불일치")
    @Size(max = 255, message = "거절 사유는 255자 이내여야 합니다")
    @NotBlank(message = "거절 사유는 필수입니다")
    private String reason;
}