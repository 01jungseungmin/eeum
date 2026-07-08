package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "채널별 도달 추정치")
    public class ChannelReachDto {
        @Schema(description = "채널", example = "KAKAO_ALERT")
        private final AiChannel channel;
        @Schema(description = "추정 도달 수", example = "120")
        private final long estimatedReach;
    }