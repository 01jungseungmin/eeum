package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "설비별 사용 비중")
    public class EquipmentShareDto {
        @Schema(description = "설비명", example = "냉방·공조")
        private final String name;
        @Schema(description = "비중 (%)", example = "38")
        private final int ratio;
    }