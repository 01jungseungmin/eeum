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
        Long messageId,

        /**
         * 이 메시지가 방의 첫 메시지인지.
         *
         * <p>수신 측에서 세면 안 된다. 이 이벤트는 AFTER_COMMIT + @Async로 처리되는데,
         * 첫 메시지의 리스너가 돌기 전에 두 번째 메시지가 커밋되면 둘 다 "첫 메시지가 아님"으로
         * 판정돼 문의 알림이 사라진다. 방 행을 잠근 발행 트랜잭션에서 확정해 실어보낸다.
         */
        boolean firstMessage
) {
}
