package com.eeum.eeum.domain.store.event;

// 사장이 리뷰에 답글을 작성했을 때 발행. 수신자는 리뷰 작성자
// NotificationType.STORE_REVIEW_REPLY 알림으로 변환
public record StoreReviewReplyCreatedEvent(
        Long reviewerAccountId,
        String storeName,
        Long storeId,
        Long reviewId
) {
}
