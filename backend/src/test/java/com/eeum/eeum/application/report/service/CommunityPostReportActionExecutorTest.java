package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.community.service.CommunityPostDeletionProcessor;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityAdminActionEvent;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostReportActionExecutorTest {

    @InjectMocks private CommunityPostReportActionExecutor executor;

    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityPostDeletionProcessor postDeletionProcessor;
    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 20L;

    @Test
    void 게시글_대상_실행기를_반환한다() {
        // when & then
        assertThat(executor.targetType()).isEqualTo(ReportTargetType.COMMUNITY_POST);
    }

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
    void 게시글_삭제_조치는_잠긴_게시글을_공통_삭제_처리기에_위임한다() {
        // given
        CommunityPost post = createPost();
        when(postRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when
        executor.execute(ReportAction.DELETE_POST, POST_ID, AUTHOR_ID, "악성 게시글");

        // then
        verify(postRepository).findWithAccountByPostIdForUpdate(POST_ID);
        verify(postDeletionProcessor).deleteLockedPost(post);
    }

    @Test
    void 작성자_경고는_신고_접수_시_저장한_작성자_ID를_사용한다() {
        // given
        when(reportedAccountActionService.apply(ReportAction.WARN_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(ReportAction.WARN_AUTHOR, POST_ID, AUTHOR_ID, "1차 경고");

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(postRepository, never()).findWithAccountByPostIdForUpdate(POST_ID);
        verify(reportedAccountActionService).apply(ReportAction.WARN_AUTHOR, AUTHOR_ID);
        verify(eventPublisher).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 작성자_정지는_공통_계정_제재_서비스에_위임한다() {
        // given
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        executor.execute(ReportAction.SUSPEND_AUTHOR, POST_ID, AUTHOR_ID, "반복 위반");

        // then
        verify(reportedAccountActionService).apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID);
        verify(eventPublisher).publishEvent(isA(CommunityAdminActionEvent.class));
    }

    @Test
    void 저장된_작성자_ID가_없으면_게시글에서_작성자를_확인한다() {
        // given
        CommunityPost post = createPost();
        when(postRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));
        when(reportedAccountActionService.apply(ReportAction.WARN_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(ReportAction.WARN_AUTHOR, POST_ID, null, "1차 경고");

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(reportedAccountActionService).apply(ReportAction.WARN_AUTHOR, AUTHOR_ID);
    }

    @Test
    void 게시글에_허용되지_않는_조치는_거부한다() {
        // when & then
        assertThatThrownBy(() ->
                executor.execute(ReportAction.DISMISS, POST_ID, AUTHOR_ID, "신고 기각"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        verify(reportedAccountActionService, never()).apply(eq(ReportAction.DISMISS), eq(AUTHOR_ID));
        verify(eventPublisher, never()).publishEvent(isA(CommunityAdminActionEvent.class));
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
