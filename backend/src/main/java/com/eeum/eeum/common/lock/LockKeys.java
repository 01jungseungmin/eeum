package com.eeum.eeum.common.lock;

public final class LockKeys {

    private LockKeys() {
    }

    public static String order(Long orderId) {
        return "lock:order:" + orderId;
    }

    // 상품별 이벤트 등록 락 — 동일 상품 ACTIVE 이벤트 중복 생성 방지
    public static String eventProductCreate(Long productId) {
        return "lock:event-product-create:" + productId;
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

    // 생성자 단위 채팅방 락(chatRoom(creatorId))은 제거됐다.
    // 비STORE GROUP 방은 중복 판정이 없어 락이 중복을 막지 못하면서, 대기 없는 락 정책 탓에
    // 정상적인 동시 요청만 LOCK_ACQUIRE_FAILED로 실패시켰기 때문. 같은 패턴을 다시 만들지 말 것.

    public static String chatRoomStore(Long storeId) {
        return "lock:chat-room:store:" + storeId;
    }

    // 방 상태 변경(생성/입장/초대/퇴장/종료) 공용 키.
    // 초대 전용 키(chatRoomInvite)는 제거했다 — 종료와 다른 키를 쓰면 종료 직전 활성 검증을
    // 통과한 입장/초대가 종료 커밋 이후에 참여자를 남긴다. 경로별로 키를 나누지 말 것.
    // 가게 단톡방은 생성과 직렬화해야 하므로 chatRoomStore(storeId)를 쓴다 (ChatRoomService 참고).
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

    // 실측값 upsert 락 (동시 저장으로 인한 unique 충돌 방지)
    public static String aiOwnerMetric(Long storeId, Object metricType, String yearMonth) {
        return "lock:ai-owner-metric:" + storeId + ":" + metricType + ":" + yearMonth;
    }

    // 절감 계획 생성 락 (동시 요청으로 인한 DRAFT 중복 생성 방지)
    public static String aiSavingPlan(Long storeId) {
        return "lock:ai-saving-plan:store:" + storeId;
    }

    // AI 메시지 수신자 발송(디스패치) 락 — 중복 발송 방지
    public static String aiMessageDispatch(Long messageId) {
        return "lock:ai-message-dispatch:" + messageId;
    }

    // 예약 발송 스케줄러 중복 실행 방지 락
    public static String aiScheduledMessageJob() {
        return "lock:ai-scheduler:scheduled-message";
    }

    // AI 플랜 결제 처리 락 (Webhook/검증 멱등)
    public static String aiPlanPayment(String paymentId) {
        return "lock:ai-plan-payment:" + paymentId;
    }

    // AI 플랜 구독 요청(결제 생성) 락 — 동일 store의 중복 요청으로 인한 이중 결제 방지
    public static String aiPlanSubscriptionRequest(Long storeId) {
        return "lock:ai-plan-subscription-request:store:" + storeId;
    }

    // 초안 보관 캡 검증~퇴거 락 — 동시 요청으로 인한 캡 초과/오래된 초안 중복 퇴거 방지
    // common 모듈이 domain을 의존하지 않도록 enum 대신 String(예: type.name())을 받는다
    public static String aiDraftCapacity(Long storeId, String type) {
        return "lock:ai-draft-capacity:" + storeId + ":" + type;
    }
}