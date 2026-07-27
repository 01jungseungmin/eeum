package com.eeum.eeum.application.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "절감 계획 저장 요청")
public class AiSavingPlanSaveRequestDto {

    @Schema(description = "저장할 절감 계획 ID (null이면 가장 최근 생성분 저장)", example = "1")
    private Long savingPlanId;
}
