package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "AI 활동 요약")
    public class ActivitySummaryDto {
        @Schema(description = "최근 30일 초안 생성 수")
        private final long draftCount;
        @Schema(description = "최근 30일 발송 처리 수")
        private final long sentCount;
        @Schema(description = "하이라이트 문구")
        private final String highlight;
    }