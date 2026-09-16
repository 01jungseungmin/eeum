package com.eeum.eeum.application.chat.dto.request;

import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "그룹(단톡방) 채팅방 생성 요청")
public class GroupChatRoomCreateRequestDto {

    @Schema(description = "채팅방 이름 (refType=STORE 이면 생략 가능 — 생략 시 '가게명 단톡방'으로 자동 설정)", example = "사장님 단톡방")
    @Size(max = 100, message = "채팅방 이름은 100자 이하여야 합니다")
    private String name;

    @Schema(description = "채팅방 타입 (GROUP / GROUP_STREET). 미전달 시 GROUP", example = "GROUP")
    private ChatRoomType type;

    @Schema(description = "연관 도메인 타입 (선택)", example = "NONE")
    private ChatRoomRefType refType;

    @Schema(description = "연관 도메인 ID (refType=STORE이면 필수·양수, 그 외 타입은 선택)")
    private Long refId;

    @Schema(description = "초대할 참여자 accountId 목록 (선택 — 미전달 시 생성자만 참여)", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private List<Long> participantAccountIds;
}
