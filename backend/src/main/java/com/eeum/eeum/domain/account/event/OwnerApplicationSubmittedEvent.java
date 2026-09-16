package com.eeum.eeum.domain.account.event;

// 사장(입점) 심사 요청이 접수되었을 때 발행. 수신자는 관리자(ROLE_ADMIN) 전체
// NotificationType.OWNER_APPLICATION_SUBMITTED 알림으로 변환된다.

public record OwnerApplicationSubmittedEvent(
        Long applicantAccountId,
        String applicantName,
        String storeName
) {
}
