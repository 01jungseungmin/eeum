package com.eeum.eeum.common.lock;

public final class LockKeys {

    private LockKeys() {
    }

    public static String order(Long orderId) {
        return "lock:order:" + orderId;
    }

    public static String orderNumber(String orderNumber) {
        return "lock:order-number:" + orderNumber;
    }

    public static String payment(String paymentId) {
        return "lock:payment:" + paymentId;
    }

    public static String portonePayment(String portonePaymentId) {
        return "lock:portone-payment:" + portonePaymentId;
    }

    public static String chatRoom(Long creatorId) {
        return "lock:chat-room:creator:" + creatorId;
    }

    public static String chatRoomStore(Long storeId) {
        return "lock:chat-room:store:" + storeId;
    }

    public static String chatRoomInvite(Long roomId) {
        return "lock:chat-room:" + roomId + ":invite";
    }

    public static String chatRoomLeave(Long roomId) {
        return "lock:chat-room:" + roomId + ":leave";
    }

    public static String reissue(Long accountId) {
        return "lock:reissue:" + accountId;
    }

    public static String accountStatus(Long accountId) {
        return "lock:account-status:" + accountId;
    }

    // 예약 생성 / 테이블 구성 변경 / 슬롯 비활성화가 공유하는 매장 단위 락
    public static String storeReservation(Long storeId) {
        return "lock:store:" + storeId + ":reservation";
    }

    // AI 사용량 체크 + 기록이 공유하는 매장 단위 락 (월 제한 초과 방지)
    public static String aiUsage(Long storeId) {
        return "lock:ai-usage:store:" + storeId;
    }

    // AI 생성 메시지 발송/예약/취소 상태 전이 락 (중복 클릭 방지)
    public static String aiMessage(Long messageId) {
        return "lock:ai-message:" + messageId;
    }

    // 생활권 매칭 노출 시작/중지 락 (중복 시작 방지)
    public static String aiExposure(Long storeId) {
        return "lock:ai-exposure:store:" + storeId;
    }
}