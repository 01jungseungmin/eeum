package com.eeum.eeum.domain.chat.event;

import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;

// 채팅 메시지를 WebSocket 채널로 브로드캐스트해야 할 때 발행
// TEXT/IMAGE/DELETE/SYSTEM — 이 이벤트로 처리
public record ChatMessageBroadcastEvent(Long roomId, ChatMessageResponseDto payload) {}
