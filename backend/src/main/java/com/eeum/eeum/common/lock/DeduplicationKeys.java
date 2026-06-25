package com.eeum.eeum.common.lock;

public final class DeduplicationKeys {

    private DeduplicationKeys() {}

    // 커뮤니티 게시글 좋아요 알림 최초 1회 보장 (likerAccountId + postId 단위)
    // TTL: 365일 (좋아요 취소 후 재시도해도 재알림 방지)
    public static final long COMMUNITY_LIKE_NOTIFIED_TTL = 365 * 24 * 60 * 60L;

    public static String communityLikeNotified(Long likerAccountId, Long postId) {
        return "dedup:community-like-notified:" + likerAccountId + ":" + postId;
    }
}
