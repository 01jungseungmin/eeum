package com.eeum.eeum.domain.notification.enums;

// NotificationSettings의 각 boolean 필드와 1:N 매핑 관계
// isAllowed() 로직은 NotificationSettings.isAllowed(type)에서 처리
public enum NotificationType {

    // 주문/결제 (사용자용 필수)
    ORDER_STATUS_CHANGED,    // 주문 상태 변경 (CONFIRMED / READY / CANCELLED)
    PAYMENT_COMPLETED,       // 결제 완료

    // 주문 (사장님 수신)
    NEW_ORDER,               // 사장님 — 새 주문 접수

    // 예약 (사용자용 필수)
    RESERVATION_CONFIRMED,   // 예약 확정
    RESERVATION_CANCELLED,   // 예약 취소
    RESERVATION_REMINDER,    // 예약 당일 리마인드

    // 예약 (사장님 수신)
    NEW_RESERVATION,         // 사장님 — 새 방문 예약 신청

    // 채팅
    CHAT_MESSAGE,            // 새 채팅 메시지

    // 커뮤니티 (댓글/대댓글/좋아요/관리자 조치)
    COMMUNITY_COMMENT,       // 내 게시글에 댓글
    COMMUNITY_REPLY,         // 내 댓글에 대댓글
    COMMUNITY_LIKE,          // 내 게시글 좋아요
    COMMUNITY_ADMIN_ACTION,  // 관리자 조치 (게시글/댓글 제재)

    // 상점/리뷰
    STORE_REVIEW,            // 새 리뷰 작성 (사장 수신)
    STORE_REVIEW_REPLY,      // 리뷰 답글 (구매자 수신)

    // 상품/재고
    STORE_PRODUCT_RESTOCK,   // 관심 상품 재입고
    STOCK_WARNING,           // 사장님 — 재고 부족 경고

    // 정산 (사장님 수신)
    SETTLEMENT_COMPLETED,    // 사장님 — 정산 완료

    // 중고거래
    USED_PRODUCT_INQUIRY,    // 중고 거래 채팅/문의
    USED_REVIEW,             // 중고 거래 후 리뷰 요청

    // 문의
    INQUIRY_ANSWERED,        // 문의 답변 완료

    // 시스템 공지 (필수)
    SYSTEM_NOTICE,           // 시스템 공지

    // 마케팅/이벤트
    MARKETING_EVENT,         // 마케팅/이벤트 알림

    // 관리자 전용
    OWNER_APPLICATION_SUBMITTED, // 새 사장 승인 신청 접수
    REPORT_SUBMITTED,            // 새 신고 접수
    INQUIRY_SUBMITTED;           // 새 문의 접수

    // UI 필터 카테고리 반환
    // NotificationController의 category 파라미터 필터링에 사용

    public NotificationCategory getCategory() {
        return switch (this) {
            case ORDER_STATUS_CHANGED, PAYMENT_COMPLETED, NEW_ORDER
                    -> NotificationCategory.ORDER;
            case RESERVATION_CONFIRMED, RESERVATION_CANCELLED,
                 RESERVATION_REMINDER, NEW_RESERVATION
                    -> NotificationCategory.RESERVATION;
            case CHAT_MESSAGE
                    -> NotificationCategory.CHAT;
            case COMMUNITY_COMMENT, COMMUNITY_REPLY, COMMUNITY_LIKE, COMMUNITY_ADMIN_ACTION
                    -> NotificationCategory.COMMUNITY;
            case STORE_REVIEW, STORE_REVIEW_REPLY
                    -> NotificationCategory.REVIEW;
            case STORE_PRODUCT_RESTOCK, STOCK_WARNING
                    -> NotificationCategory.PRODUCT;
            case SETTLEMENT_COMPLETED, INQUIRY_ANSWERED, SYSTEM_NOTICE,
                 MARKETING_EVENT, USED_PRODUCT_INQUIRY, USED_REVIEW,
                 OWNER_APPLICATION_SUBMITTED, REPORT_SUBMITTED, INQUIRY_SUBMITTED
                    -> NotificationCategory.SYSTEM;
        };
    }
}
