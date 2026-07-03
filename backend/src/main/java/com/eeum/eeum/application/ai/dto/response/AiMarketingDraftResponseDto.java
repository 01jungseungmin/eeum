package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "마케팅/공지 문구 초안 생성 결과")
public class AiMarketingDraftResponseDto {

    @Schema(description = "저장된 AI 생성 메시지 ID", example = "1")
    private final Long messageId;

    @Schema(description = "생성 제목")
    private final String title;

    @Schema(description = "생성 본문")
    private final String content;

    @Schema(description = "예상 도달 수 (채널별 합산)", example = "240")
    private final long estimatedReach;

    @Schema(description = "선택 채널 수", example = "3")
    private final int selectedChannelCount;

    @Schema(description = "글자 수", example = "42")
    private final int characterCount;

    @Schema(description = "발송 가능 여부", example = "true")
    private final boolean sendable;

    @Schema(description = "채널별 도달 추정치")
    private final List<ChannelReachDto> channelReaches;

    @Getter
    @Builder
    @Schema(description = "채널별 도달 추정치")
    public static class ChannelReachDto {
        @Schema(description = "채널", example = "KAKAO_ALERT")
        private final AiChannel channel;
        @Schema(description = "추정 도달 수", example = "120")
        private final long estimatedReach;
    }
}
