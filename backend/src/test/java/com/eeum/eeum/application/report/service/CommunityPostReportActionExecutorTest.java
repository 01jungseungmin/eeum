package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostReportActionExecutorTest {

    @InjectMocks private CommunityPostReportActionExecutor executor;

    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityPostLikeRepository postLikeRepository;
    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityCommentLikeRepository commentLikeRepository;
    @Mock private CommunityImageRepository imageRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 20L;

    @Test
    void 게시글_숨김_조치는_게시글을_숨기고_작성자에게_알림한다() {
        // given
        CommunityPost post = createPost();
        when(postRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when
        Long result = executor.execute(ReportAction.HIDE_POST, POST_ID, AUTHOR_ID, "정책 위반");

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        assertThat(post.isHidden()).isTrue();
        verify(eventPublisher).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 게시글_삭제_조치는_자식_데이터를_먼저_삭제한다() {
        // given
        CommunityPost post = createPost();
        when(postRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when
        executor.execute(ReportAction.DELETE_POST, POST_ID, AUTHOR_ID, "악성 게시글");

        // then
        InOrder order = inOrder(
                commentLikeRepository,
                commentRepository,
                postLikeRepository,
                imageRepository,
                postRepository
        );
        order.verify(commentLikeRepository).deleteByComment_Post_PostId(POST_ID);
        order.verify(commentRepository).deleteRepliesByPost_PostId(POST_ID);
        order.verify(commentRepository).deleteTopLevelCommentsByPost_PostId(POST_ID);
        order.verify(postLikeRepository).deleteByPost_PostId(POST_ID);
        order.verify(imageRepository).deleteByPost_PostId(POST_ID);
        order.verify(postRepository).delete(post);
    }

    @Test
    void 작성자_경고는_신고_접수_시_저장한_작성자_ID를_사용한다() {
        // when
        Long result = executor.execute(ReportAction.WARN_AUTHOR, POST_ID, AUTHOR_ID, "1차 경고");

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(postRepository, never()).findWithAccountByPostIdForUpdate(POST_ID);
        verify(eventPublisher).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 작성자_정지는_계정을_잠금하고_토큰_정리_이벤트를_발행한다() {
        // given
        Account author = createAuthor();
        when(accountRepository.findByIdWithLock(AUTHOR_ID)).thenReturn(Optional.of(author));

        // when
        executor.execute(ReportAction.SUSPEND_AUTHOR, POST_ID, AUTHOR_ID, "반복 위반");

        // then
        assertThat(author.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        verify(eventPublisher).publishEvent(isA(AccountTokenCleanupEvent.class));
        verify(eventPublisher).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 이미_삭제된_게시글은_숨김_조치할_수_없다() {
        // given
        when(postRepository.findWithAccountByPostIdForUpdate(POST_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                executor.execute(ReportAction.HIDE_POST, POST_ID, AUTHOR_ID, "정책 위반"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        verify(eventPublisher, never()).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    private CommunityPost createPost() {
        CommunityPost post = CommunityPost.create(createAuthor(), null, null, "제목", "본문");
        ReflectionTestUtils.setField(post, "postId", POST_ID);
        return post;
    }

    private Account createAuthor() {
        Account author = Account.createUser(
                "author@test.com", "encoded", "작성자", "작성자닉", "010-1111-2222");
        ReflectionTestUtils.setField(author, "accountId", AUTHOR_ID);
        return author;
    }
}
