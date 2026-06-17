package com.eeum.eeum.application.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "그룹 채팅방 참여자 초대 요청")
public class ParticipantInviteRequestDto {

    @Schema(description = "초대할 참여자 accountId 목록")
    @NotEmpty(message = "초대할 참여자를 1명 이상 지정해야 합니다")
    private List<Long> accountIds;
}
