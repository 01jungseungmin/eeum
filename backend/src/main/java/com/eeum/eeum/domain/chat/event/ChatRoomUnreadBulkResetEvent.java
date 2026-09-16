package com.eeum.eeum.domain.chat.event;

import java.util.List;

// 채팅방 종료 시 참여자 전원의 unread를 한 번에 회수하기 위해 발행.
// 참여자마다 ChatRoomReadEvent를 발행하면 인원수만큼 @Async 작업과 DB UPDATE가 발생한다.
public record ChatRoomUnreadBulkResetEvent(Long roomId, List<Long> accountIds) {}
