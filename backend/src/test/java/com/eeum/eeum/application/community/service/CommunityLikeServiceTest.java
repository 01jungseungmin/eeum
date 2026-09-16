package com.eeum.eeum.application.community.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityCommentLike;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.entity.CommunityPostLike;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.community.event.CommunityPostLikedEvent;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityLikeServiceTest {

    @InjectMocks
    private CommunityLikeService likeService;

    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityPostLikeRepository postLikeRepository;
    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityCommentLikeRepository commentLikeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;


    // ──────────────────── Helpers ────────────────────

    private Account createAccount(Long accountId, Long primaryRegionId) {
        Account account = Account.createUser("user@test.com", "pw", "이름", "닉네임", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", accountId);
        if (primaryRegionId != null) {
            account.setPrimaryRegion(primaryRegionId);
        }
        return account;
    }

    private Region createRegion(Long regionId) {
        Region region = Region.create("11000", "서울특별시", "강남구", "역삼동", 1000);
        ReflectionTestUtils.setField(region, "regionId", regionId);
        return region;
    }

    private CommunityPost createPost(Long postId, Account writer, Long regionId) {
        Category category = Category.createRoot(CategoryType.COMMUNITY, "커뮤니티", 1);
        ReflectionTestUtils.setField(category, "categoryId", 1L);
        Region region = createRegion(regionId);
        CommunityPost post = CommunityPost.create(writer, category, region, "제목", "내용");
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private CommunityComment createComment(Long commentId, CommunityPost post, Account writer) {
        CommunityComment comment = CommunityComment.createComment(post, writer, "댓글");
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private AccountRegion createAccountRegion(Long accountRegionId, Account account, Region region, boolean verified) {
        AccountRegion accountRegion = AccountRegion.create(account, region);
        ReflectionTestUtils.setField(accountRegion, "accountRegionId", accountRegionId);
        if (verified) {
            accountRegion.verify();
        }
        return accountRegion;
    }

    private void stubVerifiedPrimaryRegion(Long accountId, Long accountRegionId, Account account, Long regionId) {
        Region region = createRegion(regionId);
        AccountRegion accountRegion = createAccountRegion(accountRegionId, account, region, true);
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(accountRegionId, accountId))
                .thenReturn(Optional.of(accountRegion));
    }

    private void stubPostWriteLock(CommunityPost post) {
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
    }

    private void stubCommentWriteLocks(CommunityComment comment) {
        CommunityPost post = comment.getPost();
        when(commentRepository.findPostIdByCommentId(comment.getCommentId()))
                .thenReturn(Optional.of(post.getPostId()));
        stubPostWriteLock(post);
        when(commentRepository.findWithAccountByCommentIdForUpdate(comment.getCommentId()))
                .thenReturn(Optional.of(comment));
    }

    // ──────────────────── likePost ────────────────────

    @Test
    void 게시글_좋아요_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(false);
        when(postLikeRepository.saveAndFlush(any(CommunityPostLike.class)))
                .thenReturn(mock(CommunityPostLike.class));

        // when
        likeService.likePost(accountId, postId);

        // then
        verify(postLikeRepository).saveAndFlush(any(CommunityPostLike.class));
        verify(postRepository).increaseLikeCount(postId);
    }

    @Test
    void 게시글_좋아요_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 게시글_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findVisibleByPostIdForUpdate(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 게시글_좋아요_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(postId, writer, 200L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);

        verify(postLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 게시글_좋아요_대표지역_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, null);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void 게시글_좋아요_이미_존재하면_COMMUNITY_POST_LIKE_ALREADY_EXISTS() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_LIKE_ALREADY_EXISTS);

        verify(postLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 게시글_좋아요_동시_요청_DB_제약_위반시_COMMUNITY_POST_LIKE_ALREADY_EXISTS() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk"))
                .when(postLikeRepository).saveAndFlush(any(CommunityPostLike.class));

        // when & then
        assertThatThrownBy(() -> likeService.likePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_LIKE_ALREADY_EXISTS);
    }

    // ──────────────────── unlikePost ────────────────────

    @Test
    void 게시글_좋아요_취소_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);
        CommunityPostLike like = CommunityPostLike.create(account, post);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        when(postLikeRepository.findByAccount_AccountIdAndPost_PostId(accountId, postId))
                .thenReturn(Optional.of(like));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when
        likeService.unlikePost(accountId, postId);

        // then
        verify(postLikeRepository).delete(like);
        verify(postRepository).decreaseLikeCount(postId);
    }

    @Test
    void 게시글_좋아요_없으면_COMMUNITY_POST_LIKE_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        when(postLikeRepository.findByAccount_AccountIdAndPost_PostId(accountId, postId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.unlikePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_LIKE_NOT_FOUND);
    }

    @Test
    void 게시글_좋아요_취소_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(postId, writer, 200L);
        CommunityPostLike like = CommunityPostLike.create(account, post);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        when(postLikeRepository.findByAccount_AccountIdAndPost_PostId(accountId, postId))
                .thenReturn(Optional.of(like));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> likeService.unlikePost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);

        verify(postLikeRepository, never()).delete(any());
    }

    // ──────────────────── likeComment ────────────────────

    @Test
    void 댓글_좋아요_성공() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentLikeRepository.existsByAccount_AccountIdAndComment_CommentId(accountId, commentId)).thenReturn(false);
        when(commentLikeRepository.saveAndFlush(any(CommunityCommentLike.class)))
                .thenReturn(mock(CommunityCommentLike.class));

        // when
        likeService.likeComment(accountId, commentId);

        // then
        verify(commentLikeRepository).saveAndFlush(any(CommunityCommentLike.class));
        verify(commentRepository).increaseLikeCount(commentId);
    }

    @Test
    void 댓글_좋아요_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(10L, writer, 200L);
        CommunityComment comment = createComment(commentId, post, writer);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> likeService.likeComment(accountId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
    }

    @Test
    void 댓글_좋아요_이미_존재하면_COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentLikeRepository.existsByAccount_AccountIdAndComment_CommentId(accountId, commentId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> likeService.likeComment(accountId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS);

        verify(commentLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 댓글_좋아요_동시_요청_DB_제약_위반시_COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentLikeRepository.existsByAccount_AccountIdAndComment_CommentId(accountId, commentId)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("uk"))
                .when(commentLikeRepository).saveAndFlush(any(CommunityCommentLike.class));

        // when & then
        assertThatThrownBy(() -> likeService.likeComment(accountId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS);
    }

    @Test
    void 댓글_없으면_COMMUNITY_COMMENT_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.likeComment(accountId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
    }

    // ──────────────────── unlikeComment ────────────────────

    @Test
    void 댓글_좋아요_취소_성공() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);
        comment.increaseLikeCount();
        CommunityCommentLike like = CommunityCommentLike.create(account, comment);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        when(commentLikeRepository.findByAccount_AccountIdAndComment_CommentId(accountId, commentId))
                .thenReturn(Optional.of(like));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when
        likeService.unlikeComment(accountId, commentId);

        // then
        verify(commentLikeRepository).delete(like);
        verify(commentRepository).decreaseLikeCount(commentId);
    }

    @Test
    void 댓글_좋아요_없으면_COMMUNITY_COMMENT_LIKE_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        when(commentLikeRepository.findByAccount_AccountIdAndComment_CommentId(accountId, commentId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> likeService.unlikeComment(accountId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_LIKE_NOT_FOUND);
    }

    // ──────────────────── 자기 알림 방지 ────────────────────

    @Test
    void 자기_게시글_좋아요_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L); // 좋아요 누른 사람 == 게시글 작성자

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(false);
        when(postLikeRepository.saveAndFlush(any(CommunityPostLike.class)))
                .thenReturn(mock(CommunityPostLike.class));

        // when
        likeService.likePost(accountId, postId);

        // then
        verify(postLikeRepository).saveAndFlush(any(CommunityPostLike.class));
        verify(postRepository).increaseLikeCount(postId);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 타인_게시글_좋아요_시_이벤트_발행() {
        // given
        Long likerId = 1L;
        Long authorId = 2L;
        Long postId = 10L;
        Account liker = createAccount(likerId, 100L);
        Account author = createAccount(authorId, 100L);
        CommunityPost post = createPost(postId, author, 100L); // 좋아요 누른 사람 != 게시글 작성자

        when(accountRepository.findById(likerId)).thenReturn(Optional.of(liker));
        stubPostWriteLock(post);
        stubVerifiedPrimaryRegion(likerId, 100L, liker, 100L);
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(likerId, postId)).thenReturn(false);
        when(postLikeRepository.saveAndFlush(any(CommunityPostLike.class)))
                .thenReturn(mock(CommunityPostLike.class));

        // when
        likeService.likePost(likerId, postId);

        // then
        verify(eventPublisher).publishEvent(any(CommunityPostLikedEvent.class));
    }

    @Test
    void 댓글_좋아요_취소_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(10L, writer, 200L);
        CommunityComment comment = createComment(commentId, post, writer);
        CommunityCommentLike like = CommunityCommentLike.create(account, comment);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubCommentWriteLocks(comment);
        when(commentLikeRepository.findByAccount_AccountIdAndComment_CommentId(accountId, commentId))
                .thenReturn(Optional.of(like));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> likeService.unlikeComment(accountId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);

        verify(commentLikeRepository, never()).delete(any());
    }
}
