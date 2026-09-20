package com.eeum.eeum.application.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "임대가 끝난 지급 작업 인계 요청")
public record PayoutHandoverRequestDto(
        @Schema(description = "이미 송금됐다면 그 증빙(이체 번호 등), 송금되지 않았다면 확인 사유",
                example = "KB-20260920-000123")
        @NotBlank @Size(max = 100) String note
) {}
