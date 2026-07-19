package com.eeum.eeum.application.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
    @Builder
    @Schema(description = "미답변 문의 요약")
    public class UnansweredInquiryDto {
        @Schema(description = "문의 ID")
        private final Long inquiryId;
        @Schema(description = "문의 제목")
        private final String title;
        @Schema(description = "작성 시각")
        private final LocalDateTime createdAt;
    }