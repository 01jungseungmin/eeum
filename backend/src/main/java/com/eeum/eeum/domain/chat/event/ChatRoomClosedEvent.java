package com.eeum.eeum.domain.chat.event;

import java.time.LocalDateTime;

// 채팅방이 종료(soft close)됐을 때 발행 — AFTER_COMMIT 후 남아있는 구독자에게 종료를 통지
// closedAt은 DB에 기록된 실제 종료 시각. 브로드캐스트 시점(now)을 쓰면 저장값과 어긋난다.
// closedByAccountId는 관리자 강제 종료 시 null
public record ChatRoomClosedEvent(
        Long roomId,
        Long closedByAccountId,
        LocalDateTime closedAt
) {}
