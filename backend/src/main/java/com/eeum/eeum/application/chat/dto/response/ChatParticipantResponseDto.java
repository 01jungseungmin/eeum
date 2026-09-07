package com.eeum.eeum.application.chat.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.function.Function;

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

    public static ChatParticipantResponseDto from(ChatParticipant participant, Function<String, String> imageUrlResolver) {
        Account account = participant.getAccount();
        return ChatParticipantResponseDto.builder()
                .accountId(account.getAccountId())
                // name도 표시명을 담는다. 실명을 내려보내면 문의 한 번으로 상대 실명이 노출된다.
                // 필드를 없애지 않는 이유는 프론트 계약을 깨지 않기 위해서다 — nickname과 같은 값이다.
                .name(account.getDisplayName())
                .nickname(account.getDisplayName())
                .profileImageUrl(imageUrlResolver.apply(account.getProfileImageUrl()))
                .status(participant.getStatus())
                .joinedAt(participant.getJoinedAt())
                .build();
    }
}
