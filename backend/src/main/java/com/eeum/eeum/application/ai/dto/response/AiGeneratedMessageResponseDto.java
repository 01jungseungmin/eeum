package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "AI 생성 메시지")
public class AiGeneratedMessageResponseDto {

    @Schema(description = "메시지 ID", example = "1")
    private final Long messageId;

    @Schema(description = "메시지 유형", example = "CUSTOMER_CARE")
    private final AiMessageType type;

    @Schema(description = "대상 유형 (예: STORE_REVIEW, INQUIRY)")
    private final String targetType;

    @Schema(description = "대상 ID")
    private final Long targetId;

    @Schema(description = "제목")
    private final String title;

    @Schema(description = "현재 문구 (수정본)")
    private final String content;

    @Schema(description = "AI가 최초 생성한 원본 문구")
    private final String originalContent;

    @Schema(description = "발송 상태", example = "DRAFT")
    private final AiMessageStatus status;

    @Schema(description = "발송 채널", example = "APP_PUSH")
    private final AiChannel channel;

    @Schema(description = "예약 발송 시각")
    private final LocalDateTime scheduledAt;

    @Schema(description = "발송 처리 시각")
    private final LocalDateTime sentAt;

    @Schema(description = "생성 시각")
    private final LocalDateTime createdAt;

    public static AiGeneratedMessageResponseDto from(AiGeneratedMessage message) {
        return AiGeneratedMessageResponseDto.builder()
                .messageId(message.getAiGeneratedMessageId())
                .type(message.getType())
                .targetType(message.getTargetType())
                .targetId(message.getTargetId())
                .title(message.getTitle())
                .content(message.getContent())
                .originalContent(message.getOriginalContent())
                .status(message.getStatus())
                .channel(message.getChannel())
                .scheduledAt(message.getScheduledAt())
                .sentAt(message.getSentAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
