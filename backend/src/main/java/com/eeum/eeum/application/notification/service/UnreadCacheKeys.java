package com.eeum.eeum.application.notification.service;

/**
 * unread 캐시 Redis 키.
 *
 * <p>두 키는 항상 같은 DB 스냅샷으로 함께 교체되고 함께 삭제된다. 한쪽만 남으면
 * 조회가 "전체는 있는데 카테고리가 비어 있는" 상태를 완성된 캐시로 오해한다.
 * 그래서 키를 만드는 곳을 한 군데로 모은다.
 */
final class UnreadCacheKeys {

    private static final String TOTAL_PREFIX = "unread:account:";
    private static final String CATEGORY_PREFIX = "unread:category:";

    private UnreadCacheKeys() {
    }

    static String total(Long accountId) {
        return TOTAL_PREFIX + accountId;
    }

    static String category(Long accountId) {
        return CATEGORY_PREFIX + accountId;
    }
}
