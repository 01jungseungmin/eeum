package com.eeum.eeum.application.ai.dto.response;

import com.eeum.eeum.domain.ai.enums.AiChatActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
    @Builder
    @Schema(description = "챗봇 액션 카드")
    public class ChatActionDto {
        @Schema(description = "버튼 라벨", example = "이벤트 등록으로 이동")
        private final String label;
        @Schema(description = "액션 타입", example = "OPEN_EVENT_REGISTER")
        private final AiChatActionType actionType;

        public static ChatActionDto of(String label, AiChatActionType actionType) {
            return ChatActionDto.builder().label(label).actionType(actionType).build();
        }
    }