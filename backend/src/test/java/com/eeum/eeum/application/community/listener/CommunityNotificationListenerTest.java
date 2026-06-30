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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private CommunityNotificationListener listener;

    // ===================== onCommentCreated =====================

    @Test
    void 댓글_생성_시_게시글_작성자에게_COMMUNITY_COMMENT_알림_생성() {
        // given
        Long postAuthorAccountId = 1L;
        Long commenterAccountId = 2L;
        Long postId = 10L;
        Long commentId = 20L;
        CommunityCommentCreatedEvent event = new CommunityCommentCreatedEvent(
                postAuthorAccountId, commenterAccountId, postId, commentId, "닉네임A", "게시글제목"
        );
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onCommentCreated(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(postAuthorAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.COMMUNITY_COMMENT);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.COMMUNITY_POST);
        assertThat(dto.getRefId()).isEqualTo(postId);
        assertThat(dto.getLinkUrl()).isEqualTo("/community/posts/" + postId);
        assertThat(dto.getContent()).contains("닉네임A");
        verifyNoInteractions(redisUtil);
    }

    // ===================== onReplyCreated =====================

    @Test
    void 답글_생성_시_댓글_작성자에게_COMMUNITY_REPLY_알림_생성() {
        // given
        Long parentCommentAuthorAccountId = 1L;
        Long replierAccountId = 2L;
        Long postId = 10L;
        Long parentCommentId = 30L;
        Long replyId = 40L;
        CommunityReplyCreatedEvent event = new CommunityReplyCreatedEvent(
                parentCommentAuthorAccountId, replierAccountId, postId, parentCommentId, replyId, "닉네임B"
        );
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onReplyCreated(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(parentCommentAuthorAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.COMMUNITY_REPLY);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.COMMUNITY_COMMENT);
        assertThat(dto.getRefId()).isEqualTo(parentCommentId);
        assertThat(dto.getLinkUrl()).isEqualTo("/community/posts/" + postId);
        assertThat(dto.getContent()).contains("닉네임B");
        verifyNoInteractions(redisUtil);
    }

    // ===================== onPostLiked =====================

    @Test
    void 최초_좋아요_시_COMMUNITY_LIKE_알림_생성_후_Redis_dedup_키_저장() {
        // given
        Long postAuthorAccountId = 1L;
        Long likerAccountId = 2L;
        Long postId = 10L;
        CommunityPostLikedEvent event = new CommunityPostLikedEvent(
                postAuthorAccountId, likerAccountId, postId, "맛집 추천"
        );
        String dedupKey = DeduplicationKeys.communityLikeNotified(likerAccountId, postId);
        when(redisUtil.hasKey(dedupKey)).thenReturn(false);
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onPostLiked(event);

        // then
        verify(redisUtil).hasKey(eq(dedupKey));
        verify(notificationService).createNotification(captor.capture());
        verify(redisUtil).set(eq(dedupKey), eq("1"), eq(DeduplicationKeys.COMMUNITY_LIKE_NOTIFIED_TTL));

        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(postAuthorAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.COMMUNITY_LIKE);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.COMMUNITY_POST);
        assertThat(dto.getRefId()).isEqualTo(postId);
        assertThat(dto.getLinkUrl()).isEqualTo("/community/posts/" + postId);
        assertThat(dto.getContent()).contains("맛집 추천");
    }

    @Test
    void Redis_dedup_키_존재하면_좋아요_알림_스킵() {
        // given
        Long postAuthorAccountId = 1L;
        Long likerAccountId = 2L;
        Long postId = 10L;
        CommunityPostLikedEvent event = new CommunityPostLikedEvent(
                postAuthorAccountId, likerAccountId, postId, "맛집 추천"
        );
        String dedupKey = DeduplicationKeys.communityLikeNotified(likerAccountId, postId);
        when(redisUtil.hasKey(dedupKey)).thenReturn(true);

        // when
        listener.onPostLiked(event);

        // then
        verify(redisUtil).hasKey(eq(dedupKey));
        verifyNoInteractions(notificationService);
        verify(redisUtil, never()).set(any(), any(), anyLong());
    }

    // ===================== onAdminAction =====================

    @Test
    void 관리자_조치_시_대상_계정에_COMMUNITY_ADMIN_ACTION_알림_생성_linkUrl은_null() {
        // given
        Long targetAccountId = 1L;
        Long refId = 10L;
        CommunityAdminActionEvent event = new CommunityAdminActionEvent(
                targetAccountId, NotificationRefType.COMMUNITY_POST, refId, "스팸 게시물"
        );
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onAdminAction(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(targetAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.COMMUNITY_ADMIN_ACTION);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.COMMUNITY_POST);
        assertThat(dto.getRefId()).isEqualTo(refId);
        assertThat(dto.getLinkUrl()).isNull();
        assertThat(dto.getContent()).contains("스팸 게시물");
        verifyNoInteractions(redisUtil);
    }

    @Test
    void 관리자_조치_시_댓글_refType으로도_알림_생성() {
        // given
        Long targetAccountId = 1L;
        Long refId = 20L;
        CommunityAdminActionEvent event = new CommunityAdminActionEvent(
                targetAccountId, NotificationRefType.COMMUNITY_COMMENT, refId, "욕설 댓글"
        );
        ArgumentCaptor<NotificationCreateRequestDto> captor = ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onAdminAction(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.COMMUNITY_COMMENT);
        assertThat(dto.getRefId()).isEqualTo(refId);
        assertThat(dto.getContent()).contains("욕설 댓글");
    }
}
