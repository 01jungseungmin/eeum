package com.eeum.eeum.application.community.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.lock.DeduplicationKeys;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.event.CommunityCommentCreatedEvent;
import com.eeum.eeum.domain.community.event.CommunityPostLikedEvent;
import com.eeum.eeum.domain.community.event.CommunityReplyCreatedEvent;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityNotificationListener {

    private final NotificationService notificationService;
    private final RedisUtil redisUtil;

    // 내 게시글에 댓글 → 게시글 작성자에게 실시간 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommunityCommentCreatedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.postAuthorAccountId())
                .type(NotificationType.COMMUNITY_COMMENT)
                .title("새 댓글이 달렸습니다")
                .content(String.format("%s님이 회원님의 게시글에 댓글을 달았습니다.", event.commenterNickname()))
                .refType(NotificationRefType.COMMUNITY_POST)
                .refId(event.postId())
                .linkUrl("/community/posts/" + event.postId())
                .build());
    }

    // 내 댓글에 답글 → 댓글 작성자에게 실시간 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplyCreated(CommunityReplyCreatedEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.parentCommentAuthorAccountId())
                .type(NotificationType.COMMUNITY_REPLY)
                .title("새 답글이 달렸습니다")
                .content(String.format("%s님이 회원님의 댓글에 답글을 달았습니다.", event.replierNickname()))
                .refType(NotificationRefType.COMMUNITY_COMMENT)
                .refId(event.parentCommentId())
                .linkUrl("/community/posts/" + event.postId())
                .build());
    }

    // 내 게시글 좋아요 → 사용자별·게시글별 최초 1회만 (Redis dedup)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(CommunityPostLikedEvent event) {
        String dedupKey = DeduplicationKeys.communityLikeNotified(event.likerAccountId(), event.postId());

        if (redisUtil.hasKey(dedupKey)) {
            log.debug("좋아요 알림 중복 스킵: likerAccountId={}, postId={}",
                    event.likerAccountId(), event.postId());
            return;
        }

        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.postAuthorAccountId())
                .type(NotificationType.COMMUNITY_LIKE)
                .title("게시글에 좋아요가 달렸습니다")
                .content(String.format("회원님의 '%s' 게시글에 좋아요가 달렸습니다.", event.postTitle()))
                .refType(NotificationRefType.COMMUNITY_POST)
                .refId(event.postId())
                .linkUrl("/community/posts/" + event.postId())
                .build());

        redisUtil.set(dedupKey, "1", DeduplicationKeys.COMMUNITY_LIKE_NOTIFIED_TTL);
    }

    // 관리자 조치 → 게시글/댓글 작성자에게 필수 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminAction(CommunityAdminActionEvent event) {
        notificationService.createNotification(NotificationCreateRequestDto.builder()
                .accountId(event.targetAccountId())
                .type(NotificationType.COMMUNITY_ADMIN_ACTION)
                .title("관리자 조치 안내")
                .content(String.format("회원님의 게시물에 관리자 조치가 취해졌습니다. 사유: %s", event.reason()))
                .refType(event.refType())
                .refId(event.refId())
                .linkUrl(null)
                .build());
    }
}
