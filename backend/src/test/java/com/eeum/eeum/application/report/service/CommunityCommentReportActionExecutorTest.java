package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityCommentReportActionExecutorTest {

    @InjectMocks private CommunityCommentReportActionExecutor executor;

    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityPostRepository postRepository;
    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long COMMENT_ID = 10L;
    private static final Long AUTHOR_ID = 20L;
    private static final String ADMIN_NOTE = "운영 정책 위반";

    @Test
    void 댓글_신고_대상_타입을_지원한다() {
        // when
        ReportTargetType result = executor.targetType();

        // then
        assertThat(result).isEqualTo(ReportTargetType.COMMUNITY_COMMENT);
    }

    @Test
    void 댓글_삭제_조치는_댓글을_잠금_조회해_soft_delete하고_작성자에게_알림한다() {
        // given
        CommunityComment comment = createComment();
        when(commentRepository.findWithAccountAndPostByCommentId(COMMENT_ID))
                .thenReturn(Optional.of(comment));
        when(postRepository.findWithAccountByPostIdForUpdate(comment.getPost().getPostId()))
                .thenReturn(Optional.of(comment.getPost()));
        when(commentRepository.findWithAccountByCommentIdForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(comment));

        // when
        Long result = executor.execute(ReportAction.DELETE_COMMENT, COMMENT_ID, AUTHOR_ID, ADMIN_NOTE);

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isEqualTo("삭제된 댓글입니다.");
        verify(postRepository).decreaseCommentCount(comment.getPost().getPostId());
        verify(reportedAccountActionService, never()).apply(ReportAction.DELETE_COMMENT, AUTHOR_ID);
        assertAdminActionEvent(ReportAction.DELETE_COMMENT, "댓글 삭제");
    }

    @Test
    void 작성자_경고는_신고_접수_시_저장한_작성자_ID를_우선_사용한다() {
        // given
        when(reportedAccountActionService.apply(ReportAction.WARN_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(ReportAction.WARN_AUTHOR, COMMENT_ID, AUTHOR_ID, ADMIN_NOTE);

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(commentRepository, never()).findWithAccountByCommentIdForUpdate(COMMENT_ID);
        verify(reportedAccountActionService).apply(ReportAction.WARN_AUTHOR, AUTHOR_ID);
        assertAdminActionEvent(ReportAction.WARN_AUTHOR, "작성자 경고");
    }

    @Test
    void 저장된_작성자_ID가_없으면_댓글을_잠금_조회해_정지한다() {
        // given
        CommunityComment comment = createComment();
        when(commentRepository.findWithAccountByCommentIdForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(comment));
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(ReportAction.SUSPEND_AUTHOR, COMMENT_ID, null, ADMIN_NOTE);

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(reportedAccountActionService).apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID);
        assertAdminActionEvent(ReportAction.SUSPEND_AUTHOR, "작성자 정지");
    }

    @Test
    void 작성자_ID를_복원할_댓글이_없으면_조치할_수_없다() {
        // given
        when(commentRepository.findWithAccountByCommentIdForUpdate(COMMENT_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                executor.execute(ReportAction.WARN_AUTHOR, COMMENT_ID, null, ADMIN_NOTE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        verify(reportedAccountActionService, never()).apply(ReportAction.WARN_AUTHOR, AUTHOR_ID);
        verify(eventPublisher, never()).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 이미_삭제된_댓글은_다시_삭제할_수_없다() {
        // given
        CommunityComment comment = createComment();
        comment.softDelete();
        when(commentRepository.findWithAccountAndPostByCommentId(COMMENT_ID))
                .thenReturn(Optional.of(comment));
        when(postRepository.findWithAccountByPostIdForUpdate(comment.getPost().getPostId()))
                .thenReturn(Optional.of(comment.getPost()));
        when(commentRepository.findWithAccountByCommentIdForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() ->
                executor.execute(ReportAction.DELETE_COMMENT, COMMENT_ID, AUTHOR_ID, ADMIN_NOTE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        verify(eventPublisher, never()).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 댓글에_허용되지_않은_조치는_거부한다() {
        // when & then
        assertThatThrownBy(() ->
                executor.execute(ReportAction.HIDE_POST, COMMENT_ID, AUTHOR_ID, ADMIN_NOTE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        verify(eventPublisher, never()).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    private void assertAdminActionEvent(ReportAction action, String expectedReason) {
        ArgumentCaptor<CommunityAdminActionEvent> captor =
                ArgumentCaptor.forClass(CommunityAdminActionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        CommunityAdminActionEvent event = captor.getValue();
        assertThat(event.targetAccountId()).isEqualTo(AUTHOR_ID);
        assertThat(event.refType()).isEqualTo(NotificationRefType.COMMUNITY_COMMENT);
        assertThat(event.refId()).isEqualTo(COMMENT_ID);
        assertThat(event.reason()).isEqualTo(expectedReason);
        assertThat(action).isIn(
                ReportAction.DELETE_COMMENT,
                ReportAction.WARN_AUTHOR,
                ReportAction.SUSPEND_AUTHOR
        );
    }

    private CommunityComment createComment() {
        Account author = Account.createUser(
                "author@test.com", "encoded", "작성자", "작성자닉", "010-1111-2222");
        ReflectionTestUtils.setField(author, "accountId", AUTHOR_ID);

        CommunityPost post = CommunityPost.create(author, null, null, "제목", "본문");
        CommunityComment comment = CommunityComment.createComment(post, author, "신고된 댓글");
        ReflectionTestUtils.setField(comment, "commentId", COMMENT_ID);
        return comment;
    }
}
