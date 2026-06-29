package com.eeum.eeum.domain.community.event;

public record CommunityCommentCreatedEvent(
        Long postAuthorAccountId,
        Long commenterAccountId,
        Long postId,
        Long commentId,
        String commenterNickname,
        String postTitle
) {}
