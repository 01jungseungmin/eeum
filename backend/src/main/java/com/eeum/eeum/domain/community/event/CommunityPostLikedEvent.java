package com.eeum.eeum.domain.community.event;

public record CommunityPostLikedEvent(
        Long postAuthorAccountId,
        Long likerAccountId,
        Long postId,
        String postTitle
) {}
