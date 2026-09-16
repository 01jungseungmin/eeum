package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "활동 지역 등록 요청")
public class RegionRequestDto {

    @Schema(description = "지역 ID", example = "1")
    @NotNull(message = "지역 ID는 필수입니다")
    private Long regionId;
}
