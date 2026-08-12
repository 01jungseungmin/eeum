package com.eeum.eeum.domain.store.event;

public record StoreReviewAdminActionEvent(
        Long targetAccountId,
        Long reviewId,
        String reason
) {}
