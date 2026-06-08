package com.eeum.eeum.domain.notification.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 알림 UI 필터 카테고리.
 * 웹/앱 탭 필터에서 사용한다.
 */
public enum NotificationCategory {

    ORDER,       // 주문
    CHAT,        // 채팅
    REVIEW,      // 리뷰
    RESERVATION, // 예약
    PRODUCT,     // 상품/재고
    SYSTEM;      // 시스템/정산/기타

    /** 해당 카테고리에 속하는 NotificationType 목록 반환 */
    public List<NotificationType> getTypes() {
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.getCategory() == this)
                .toList();
    }
}
