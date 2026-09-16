package com.eeum.eeum.application.chat.dto.request;

import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "관리자 채팅방 검색 조건")
public class ChatRoomAdminSearchDto {

    @Schema(description = "채팅방 타입 필터")
    private ChatRoomType type;

    @Schema(description = "활성 여부 필터")
    private Boolean isActive;

    @Schema(description = "생성일 시작")
    private LocalDateTime from;

    @Schema(description = "생성일 종료")
    private LocalDateTime to;
}
