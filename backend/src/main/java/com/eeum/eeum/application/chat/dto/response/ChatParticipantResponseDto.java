package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅방 참여자 정보")
public class ChatParticipantResponseDto {

    private final Long accountId;
    private final String name;
    private final String nickname;
    private final String profileImageUrl;
    private final ParticipantStatus status;
    private final LocalDateTime joinedAt;

    public static ChatParticipantResponseDto from(ChatParticipant participant) {
        Account account = participant.getAccount();
        return ChatParticipantResponseDto.builder()
                .accountId(account.getAccountId())
                .name(account.getName())
                .nickname(account.getNickname())
                .profileImageUrl(account.getProfileImageUrl())
                .status(participant.getStatus())
                .joinedAt(participant.getJoinedAt())
                .build();
    }
}
