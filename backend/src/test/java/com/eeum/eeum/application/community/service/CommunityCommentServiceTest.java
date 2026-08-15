package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.request.CommunityCommentCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityCommentUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityCommentResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.event.CommunityCommentCreatedEvent;
import com.eeum.eeum.domain.community.event.CommunityReplyCreatedEvent;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityCommentServiceTest {

    @InjectMocks
    private CommunityCommentService commentService;

    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityCommentLikeRepository commentLikeRepository;
    @Mock private CommunityPostRepository postRepository;
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
        CommunityComment comment = CommunityComment.createComment(post, writer, "댓글 내용");
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private CommunityComment createReply(Long replyId, CommunityPost post, Account writer, CommunityComment parent) {
        CommunityComment reply = CommunityComment.createReply(post, writer, parent, "대댓글 내용");
        ReflectionTestUtils.setField(reply, "commentId", replyId);
        return reply;
    }

    private CommunityCommentCreateRequestDto createCommentRequest(String content) {
        CommunityCommentCreateRequestDto req = new CommunityCommentCreateRequestDto();
        ReflectionTestUtils.setField(req, "content", content);
        return req;
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

    // ──────────────────── getComments ────────────────────

    @Test
    void 댓글_목록_조회_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);
        CommunityComment comment = createComment(20L, post, account);

        Pageable pageable = PageRequest.of(0, 10);
        Page<CommunityComment> page = new PageImpl<>(List.of(comment), pageable, 1);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.findByPost_PostIdAndParentCommentIsNull(postId, pageable)).thenReturn(page);
        when(commentLikeRepository.findLikedCommentIds(eq(accountId), anyList())).thenReturn(Set.of(20L));

        // when
        Page<CommunityCommentResponseDto> result = commentService.getComments(accountId, postId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isLikedByMe()).isTrue();
    }

    @Test
    void 댓글_목록_조회_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.getComments(accountId, 10L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 댓글_목록_조회_게시글_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 999L;
        Account account = createAccount(accountId, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.getComments(accountId, postId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 댓글_목록_조회_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(postId, writer, 200L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> commentService.getComments(accountId, postId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
    }

    // ──────────────────── getReplies ────────────────────

    @Test
    void 대댓글_목록_조회_성공() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentCommentId, post, account);
        CommunityComment reply = createReply(30L, post, account, parent);

        Pageable pageable = PageRequest.of(0, 10);
        Page<CommunityComment> replyPage = new PageImpl<>(List.of(reply), pageable, 1);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(postRepository.findById(post.getPostId())).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.findByParentComment_CommentId(parentCommentId, pageable)).thenReturn(replyPage);
        when(commentLikeRepository.findLikedCommentIds(eq(accountId), anyList())).thenReturn(Set.of());

        // when
        Page<CommunityCommentResponseDto> result = commentService.getReplies(accountId, parentCommentId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isReply()).isTrue();
    }

    @Test
    void 대댓글_목록_조회_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.getReplies(accountId, 20L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 대댓글_목록_조회_부모댓글_없으면_COMMUNITY_COMMENT_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.getReplies(accountId, 999L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
    }

    @Test
    void 대댓글에_대댓글_조회_시도_COMMUNITY_REPLY_DEPTH_EXCEEDED() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(20L, post, account);
        CommunityComment reply = createReply(30L, post, account, parent);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findById(30L)).thenReturn(Optional.of(reply));

        // when & then
        assertThatThrownBy(() -> commentService.getReplies(accountId, 30L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_REPLY_DEPTH_EXCEEDED);
    }

    @Test
    void 대댓글_목록_조회_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(10L, writer, 200L);
        CommunityComment parent = createComment(parentCommentId, post, writer);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(postRepository.findById(post.getPostId())).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> commentService.getReplies(accountId, parentCommentId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
    }

    @Test
    void 숨김_게시글의_대댓글_목록은_COMMUNITY_POST_NOT_FOUND() {
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentCommentId, post, account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(postRepository.findById(post.getPostId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getReplies(
                accountId, parentCommentId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    // ──────────────────── createComment ────────────────────

    @Test
    void 댓글_작성_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);
        CommunityCommentCreateRequestDto request = createCommentRequest("댓글 내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 20L);
            return saved;
        });

        // when
        CommunityCommentResponseDto result = commentService.createComment(accountId, postId, request);

        // then
        assertThat(result.getContent()).isEqualTo("댓글 내용");
        assertThat(result.isReply()).isFalse();
        verify(postRepository).increaseCommentCount(postId);
    }

    @Test
    void 댓글_작성_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        Long postId = 10L;
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.createComment(accountId, postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 댓글_작성_게시글_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 999L;
        Account account = createAccount(accountId, 100L);
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.createComment(accountId, postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 댓글_작성_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(postId, writer, 200L);
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> commentService.createComment(accountId, postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);

        verify(commentRepository, never()).save(any());
    }

    // ──────────────────── createReply ────────────────────

    @Test
    void 대댓글_작성_성공() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentCommentId, post, account);
        CommunityCommentCreateRequestDto request = createCommentRequest("대댓글 내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(parentCommentId))
                .thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(parentCommentId))
                .thenReturn(Optional.of(parent));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 30L);
            return saved;
        });

        // when
        CommunityCommentResponseDto result = commentService.createReply(accountId, parentCommentId, request);

        // then
        assertThat(result.getContent()).isEqualTo("대댓글 내용");
        assertThat(result.isReply()).isTrue();
        verify(postRepository).increaseCommentCount(post.getPostId());
    }

    @Test
    void 대댓글_작성_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        Long parentCommentId = 20L;
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.createReply(accountId, parentCommentId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 대댓글_작성_부모댓글_없으면_COMMUNITY_COMMENT_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.createReply(accountId, 999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
    }

    @Test
    void 삭제된_부모댓글에는_대댓글을_작성할_수_없다() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentCommentId, post, account);
        parent.softDelete();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(parentCommentId))
                .thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(parentCommentId))
                .thenReturn(Optional.of(parent));

        // when & then
        assertThatThrownBy(() -> commentService.createReply(
                accountId,
                parentCommentId,
                createCommentRequest("대댓글")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).increaseCommentCount(anyLong());
    }

    @Test
    void 대댓글에_대댓글_작성_시도시_COMMUNITY_REPLY_DEPTH_EXCEEDED() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(20L, post, account);
        CommunityComment replyAsParent = createReply(30L, post, account, parent);
        CommunityCommentCreateRequestDto request = createCommentRequest("3depth 시도");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(30L)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(30L))
                .thenReturn(Optional.of(replyAsParent));

        // when & then
        assertThatThrownBy(() -> commentService.createReply(accountId, 30L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_REPLY_DEPTH_EXCEEDED);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void 대댓글_작성_다른_지역이면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        Account writer = createAccount(2L, 200L);
        CommunityPost post = createPost(10L, writer, 200L);
        CommunityComment parent = createComment(parentCommentId, post, writer);
        CommunityCommentCreateRequestDto request = createCommentRequest("내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(parentCommentId))
                .thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(parentCommentId))
                .thenReturn(Optional.of(parent));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);

        // when & then
        assertThatThrownBy(() -> commentService.createReply(accountId, parentCommentId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
    }

    // ──────────────────── updateComment ────────────────────

    @Test
    void 댓글_수정_성공() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        CommunityCommentUpdateRequestDto request = new CommunityCommentUpdateRequestDto();
        ReflectionTestUtils.setField(request, "content", "수정된 내용");

        when(commentRepository.findPostIdByCommentId(commentId)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByAccount_AccountIdAndComment_CommentId(accountId, commentId))
                .thenReturn(false);

        // when
        CommunityCommentResponseDto result = commentService.updateComment(accountId, commentId, request);

        // then
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void 댓글_수정_다른_사용자면_COMMUNITY_COMMENT_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long otherId = 2L;
        Long commentId = 20L;
        Account owner = createAccount(ownerId, 100L);
        CommunityPost post = createPost(10L, owner, 100L);
        CommunityComment comment = createComment(commentId, post, owner);

        CommunityCommentUpdateRequestDto request = new CommunityCommentUpdateRequestDto();

        when(commentRepository.findPostIdByCommentId(commentId)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(commentId))
                .thenReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(otherId, commentId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_ACCESS_DENIED);
    }

    @Test
    void 댓글_수정_댓글_없으면_COMMUNITY_COMMENT_NOT_FOUND() {
        // given
        when(commentRepository.findPostIdByCommentId(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.updateComment(1L, 999L, new CommunityCommentUpdateRequestDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
    }

    // ──────────────────── deleteComment ────────────────────

    @Test
    void 댓글_삭제_시_부모_댓글만_soft_delete_되고_대댓글은_유지된다() {
        // given
        Long accountId = 1L;
        Long commentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment comment = createComment(commentId, post, account);

        when(commentRepository.findPostIdByCommentId(commentId)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findWithAccountByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(commentId))
                .thenReturn(Optional.of(comment));

        // when
        commentService.deleteComment(accountId, commentId);

        // then
        verify(postRepository).decreaseCommentCount(post.getPostId());
        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void 대댓글_soft_delete_성공() {
        // given
        Long accountId = 1L;
        Long parentId = 20L;
        Long replyId = 30L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentId, post, account);
        CommunityComment reply = createReply(replyId, post, account, parent);

        when(commentRepository.findPostIdByCommentId(replyId)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findWithAccountByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(replyId))
                .thenReturn(Optional.of(reply));

        // when
        commentService.deleteComment(accountId, replyId);

        // then
        verify(postRepository).decreaseCommentCount(post.getPostId());
        assertThat(reply.isDeleted()).isTrue();
    }

    @Test
    void 댓글_삭제_다른_사용자면_COMMUNITY_COMMENT_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long otherId = 2L;
        Long commentId = 20L;
        Account owner = createAccount(ownerId, 100L);
        CommunityPost post = createPost(10L, owner, 100L);
        CommunityComment comment = createComment(commentId, post, owner);

        when(commentRepository.findPostIdByCommentId(commentId)).thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findWithAccountByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(commentId))
                .thenReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(otherId, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_ACCESS_DENIED);
    }

    @Test
    void 댓글_삭제_댓글_없으면_COMMUNITY_COMMENT_NOT_FOUND() {
        // given
        when(commentRepository.findPostIdByCommentId(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
    }

    @Test
    void 댓글_삭제_도중_게시글이_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        Long commentId = 20L;
        when(commentRepository.findPostIdByCommentId(commentId)).thenReturn(Optional.of(10L));
        when(postRepository.findWithAccountByPostIdForUpdate(10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.deleteComment(1L, commentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        verify(commentRepository, never()).findWithAccountByCommentIdForUpdate(commentId);
    }

    // ──────────────────── 자기 알림 방지 ────────────────────

    @Test
    void 자기_게시글에_댓글_작성_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L); // 댓글 작성자 == 게시글 작성자
        CommunityCommentCreateRequestDto request = createCommentRequest("셀프 댓글");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 20L);
            return saved;
        });

        // when
        commentService.createComment(accountId, postId, request);

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 타인_게시글에_댓글_작성_시_이벤트_발행() {
        // given
        Long commenterId = 1L;
        Long authorId = 2L;
        Long postId = 10L;
        Account commenter = createAccount(commenterId, 100L);
        Account author = createAccount(authorId, 100L);
        CommunityPost post = createPost(postId, author, 100L); // 댓글 작성자 != 게시글 작성자
        CommunityCommentCreateRequestDto request = createCommentRequest("댓글");

        when(accountRepository.findById(commenterId)).thenReturn(Optional.of(commenter));
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        stubVerifiedPrimaryRegion(commenterId, 100L, commenter, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 20L);
            return saved;
        });

        // when
        commentService.createComment(commenterId, postId, request);

        // then
        verify(eventPublisher).publishEvent(any(CommunityCommentCreatedEvent.class));
    }

    @Test
    void 자기_댓글에_답글_작성_시_이벤트_미발행() {
        // given
        Long accountId = 1L;
        Long parentCommentId = 20L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(10L, account, 100L);
        CommunityComment parent = createComment(parentCommentId, post, account); // 답글 작성자 == 댓글 작성자
        CommunityCommentCreateRequestDto request = createCommentRequest("셀프 답글");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commentRepository.findPostIdByCommentId(parentCommentId))
                .thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(parentCommentId))
                .thenReturn(Optional.of(parent));
        stubVerifiedPrimaryRegion(accountId, 100L, account, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 30L);
            return saved;
        });

        // when
        commentService.createReply(accountId, parentCommentId, request);

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 타인_댓글에_답글_작성_시_이벤트_발행() {
        // given
        Long replierId = 1L;
        Long commentAuthorId = 2L;
        Long parentCommentId = 20L;
        Account replier = createAccount(replierId, 100L);
        Account commentAuthor = createAccount(commentAuthorId, 100L);
        CommunityPost post = createPost(10L, commentAuthor, 100L);
        CommunityComment parent = createComment(parentCommentId, post, commentAuthor); // 답글 작성자 != 댓글 작성자
        CommunityCommentCreateRequestDto request = createCommentRequest("답글");

        when(accountRepository.findById(replierId)).thenReturn(Optional.of(replier));
        when(commentRepository.findPostIdByCommentId(parentCommentId))
                .thenReturn(Optional.of(post.getPostId()));
        when(postRepository.findVisibleByPostIdForUpdate(post.getPostId()))
                .thenReturn(Optional.of(post));
        when(commentRepository.findWithAccountByCommentIdForUpdate(parentCommentId))
                .thenReturn(Optional.of(parent));
        stubVerifiedPrimaryRegion(replierId, 100L, replier, 100L);
        when(commentRepository.save(any(CommunityComment.class))).thenAnswer(inv -> {
            CommunityComment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 30L);
            return saved;
        });

        // when
        commentService.createReply(replierId, parentCommentId, request);

        // then
        verify(eventPublisher).publishEvent(any(CommunityReplyCreatedEvent.class));
    }
}
