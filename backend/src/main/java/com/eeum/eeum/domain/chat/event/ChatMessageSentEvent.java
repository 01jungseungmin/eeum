package com.eeum.eeum.domain.chat.event;

// 채팅 메시지(TEXT/IMAGE) 발송 완료 시 발행 (트랜잭션 커밋 후 처리)
// 수신자별 unread 증가 및 푸시 알림으로 변환
// 표시용 데이터는 발행 트랜잭션 내에서 미리 캡처
public record ChatMessageSentEvent(
        Long roomId,
        String roomName,
        Long senderAccountId,
        String senderName,
        String preview,
        Long messageId
) {
}
