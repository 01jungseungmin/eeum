package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.enums.ParticipantStatus;

/**
 * WebSocket SUBSCRIBE/SEND 인가에 필요한 최소 정보만 담은 projection.
 * 엔티티를 로딩하지 않고 스칼라 두 개만 조회한다 — 메시지 1건마다 호출되는 경로라 비용에 민감하다.
 */
public record ChatAccessStatus(ParticipantStatus participantStatus, boolean roomActive) {

    public boolean isActiveParticipant() {
        return participantStatus == ParticipantStatus.ACTIVE;
    }
}
