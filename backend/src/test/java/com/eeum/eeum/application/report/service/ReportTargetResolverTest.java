package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.community.entity.CommunityComment;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTargetResolverTest {

    @InjectMocks
    private ReportTargetResolver reportTargetResolver;

    @Mock private StoreRepository storeRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private CommunityPostRepository communityPostRepository;
    @Mock private CommunityCommentRepository communityCommentRepository;
    @Mock private AccountRepository accountRepository;

    private static final Long OWNER_ID = 7L;

    // ===================== 픽스처 헬퍼 =====================

    private Account createAccount(Long id, String name, String nickname) {
        Account account = Account.createUser(
                "user" + id + "@test.com", "encoded-pw", name, nickname, "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private Store createStore(Long id, Account owner) {
        Store store = Store.createForOwnerSignup(owner, "이음 카페", "서울시 강남구", "010-1111-2222");
        ReflectionTestUtils.setField(store, "storeId", id);
        ReflectionTestUtils.setField(store, "description", "동네 카페입니다");
        return store;
    }

    // ===================== 타입별 대상 조회 =====================

    @Test
    void STORE_신고는_가게명과_소유자_정보를_반환한다() {
        // Given
        Account owner = createAccount(OWNER_ID, "김사장", "사장님");
        when(storeRepository.findWithAccountByStoreId(1L))
                .thenReturn(Optional.of(createStore(1L, owner)));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.STORE, 1L);

        // Then
        assertThat(result.isExists()).isTrue();
        assertThat(result.getTitle()).isEqualTo("이음 카페");
        assertThat(result.getContent()).isEqualTo("동네 카페입니다");
        assertThat(result.getContentPreview()).isEqualTo("동네 카페입니다");
        assertThat(result.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(result.getOwnerName()).isEqualTo("김사장");
        assertThat(result.getOwnerNickname()).isEqualTo("사장님");
    }

    @Test
    void STORE_REVIEW_신고는_작성자와_상위_가게_정보를_함께_반환한다() {
        // Given
        Account owner = createAccount(OWNER_ID, "김사장", "사장님");
        Account reviewer = createAccount(20L, "이리뷰", "리뷰어");
        Store store = createStore(1L, owner);
        StoreReview review = StoreReview.createForOrder(store, reviewer, null, 1, "최악이에요");
        ReflectionTestUtils.setField(review, "storereviewId", 30L);
        when(storeReviewRepository.findWithAccountAndStoreByStorereviewId(30L))
                .thenReturn(Optional.of(review));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.STORE_REVIEW, 30L);

        // Then
        assertThat(result.isExists()).isTrue();
        assertThat(result.getTitle()).isEqualTo("별점 1점");
        assertThat(result.getContent()).isEqualTo("최악이에요");
        assertThat(result.getContentPreview()).isEqualTo("최악이에요");
        assertThat(result.getOwnerAccountId()).isEqualTo(20L);
        assertThat(result.getParentTitle()).isEqualTo("이음 카페");
        assertThat(result.getParentId()).isEqualTo(1L);
    }

    @Test
    void COMMUNITY_POST_신고는_제목과_본문_미리보기를_반환한다() {
        // Given
        Account author = createAccount(20L, "박작성", "작성자");
        CommunityPost post = CommunityPost.create(author, null, null, "신고 대상 게시글", "부적절한 본문");
        ReflectionTestUtils.setField(post, "postId", 40L);
        when(communityPostRepository.findWithAccountByPostId(40L)).thenReturn(Optional.of(post));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, 40L);

        // Then
        assertThat(result.isExists()).isTrue();
        assertThat(result.getTitle()).isEqualTo("신고 대상 게시글");
        assertThat(result.getContent()).isEqualTo("부적절한 본문");
        assertThat(result.getContentPreview()).isEqualTo("부적절한 본문");
        assertThat(result.getOwnerAccountId()).isEqualTo(20L);
    }

    @Test
    void COMMUNITY_COMMENT_신고는_상위_게시글_제목을_함께_반환한다() {
        // Given
        Account author = createAccount(20L, "박작성", "작성자");
        CommunityPost post = CommunityPost.create(author, null, null, "원본 게시글", "본문");
        ReflectionTestUtils.setField(post, "postId", 40L);
        CommunityComment comment = CommunityComment.createComment(post, author, "욕설 댓글");
        ReflectionTestUtils.setField(comment, "commentId", 50L);
        when(communityCommentRepository.findWithAccountAndPostByCommentId(50L))
                .thenReturn(Optional.of(comment));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.COMMUNITY_COMMENT, 50L);

        // Then
        assertThat(result.isExists()).isTrue();
        assertThat(result.getContent()).isEqualTo("욕설 댓글");
        assertThat(result.getContentPreview()).isEqualTo("욕설 댓글");
        assertThat(result.getParentTitle()).isEqualTo("원본 게시글");
        assertThat(result.getParentId()).isEqualTo(40L);
    }

    @Test
    void 삭제된_댓글은_현재_대상_미존재로_판정한다() {
        // Given
        Account author = createAccount(20L, "박작성", "작성자");
        CommunityPost post = CommunityPost.create(author, null, null, "원본 게시글", "본문");
        ReflectionTestUtils.setField(post, "postId", 40L);
        CommunityComment comment = CommunityComment.createComment(post, author, "욕설 댓글");
        ReflectionTestUtils.setField(comment, "commentId", 50L);
        ReflectionTestUtils.setField(comment, "deleted", true);
        when(communityCommentRepository.findWithAccountAndPostByCommentId(50L))
                .thenReturn(Optional.of(comment));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.COMMUNITY_COMMENT, 50L);

        // Then
        assertThat(result.isExists()).isFalse();
        assertThat(result.getContentPreview()).isNull();
        assertThat(result.getOwnerAccountId()).isNull();
    }

    @Test
    void ACCOUNT_신고는_대상_사용자_본인을_소유자로_반환한다() {
        // Given
        Account target = createAccount(60L, "최유저", "유저닉");
        when(accountRepository.findById(60L)).thenReturn(Optional.of(target));

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.ACCOUNT, 60L);

        // Then
        assertThat(result.isExists()).isTrue();
        assertThat(result.getTitle()).isEqualTo("최유저");
        assertThat(result.getOwnerAccountId()).isEqualTo(60L);
        assertThat(result.getOwnerNickname()).isEqualTo("유저닉");
    }

    @Test
    void 신고_접수_후_삭제된_대상은_예외가_아니라_exists_false로_반환된다() {
        // Given: 관리자는 삭제된 콘텐츠에 대한 신고도 열람/처리할 수 있어야 한다
        when(communityPostRepository.findWithAccountByPostId(99L)).thenReturn(Optional.empty());

        // When
        ReportTargetSnapshotDto result = reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, 99L);

        // Then
        assertThat(result.isExists()).isFalse();
        assertThat(result.getTargetType()).isEqualTo(ReportTargetType.COMMUNITY_POST);
        assertThat(result.getTargetId()).isEqualTo(99L);
        assertThat(result.getTitle()).isNull();
    }

    @Test
    void 대상_조회는_소유자를_함께_로드해_추가_쿼리가_발생하지_않는다() {
        // Given: LAZY 소유자 접근으로 N+1이 나지 않도록 fetch join 전용 메서드를 쓰는지 검증
        Account owner = createAccount(OWNER_ID, "김사장", "사장님");
        when(storeRepository.findWithAccountByStoreId(1L))
                .thenReturn(Optional.of(createStore(1L, owner)));

        // When
        reportTargetResolver.resolve(ReportTargetType.STORE, 1L);

        // Then: 소유자 조회를 위한 AccountRepository 접근이 없어야 한다
        org.mockito.Mockito.verifyNoInteractions(accountRepository);
    }
}
