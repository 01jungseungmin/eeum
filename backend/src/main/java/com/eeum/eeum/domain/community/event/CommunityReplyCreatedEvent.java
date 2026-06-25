package com.eeum.eeum.domain.community.event;

public record CommunityReplyCreatedEvent(
        Long parentCommentAuthorAccountId,
        Long replierAccountId,
        Long postId,
        Long parentCommentId,
        Long replyId,
        String replierNickname
) {}
