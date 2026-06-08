package com.eeum.eeum.domain.notification.enums;

/**
 * 알림 Polymorphic 참조 타입.
 * refId는 해당 도메인의 PK를 가리킨다.
 */
public enum NotificationRefType {
    ORDER,
    PAYMENT,
    RESERVATION,
    CHAT_ROOM,
    COMMUNITY_POST,
    COMMUNITY_COMMENT,
    STORE,
    STORE_REVIEW,
    PRODUCT,
    USED_PRODUCT,
    USED_REVIEW,
    INQUIRY,
    SETTLEMENT,          // 정산
    OWNER_APPLICATION,   // 사장 승인 신청
    REPORT,              // 신고
    SYSTEM               // refId = null (시스템 공지)
}
