package com.eeum.eeum.domain.store.event;

// 새 상점 리뷰가 작성되었을 때 발행. 수신자는 가게 사장.
// NotificationType.STORE_REVIEW 알림으로 변환
public record StoreReviewCreatedEvent(
        Long ownerAccountId,
        String reviewerName,
        String storeName,
        Long storeId,
        Long reviewId
) {
}
