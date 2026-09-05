package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;

/**
 * WebSocket SUBSCRIBE/SEND 인가에 필요한 최소 정보만 담은 projection.
 * 엔티티를 로딩하지 않고 스칼라 세 개만 조회한다 — 메시지 1건마다 호출되는 경로라 비용에 민감하다.
 *
 * <p>발신자 계정 상태까지 담는다. Access Token은 만료(30분) 전까지 유효하므로
 * 정지·탈퇴 직후에도 이미 열린 연결로 메시지를 계속 보낼 수 있다 — 매 요청 확인이 유일한 차단점이다.
 */
public record ChatAccessStatus(
        ParticipantStatus participantStatus,
        boolean roomActive,
        AccountStatus accountStatus) {

    public boolean isActiveParticipant() {
        return participantStatus == ParticipantStatus.ACTIVE;
    }
}
