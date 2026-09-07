package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.request.CommunityPostCreateRequestDto;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.community.dto.request.CommunityPostUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.application.account.service.PrimaryRegionResolver;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @InjectMocks
    private CommunityPostService postService;

    @Mock private FileStorageService fileStorageService;
    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityPostLikeRepository postLikeRepository;
    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityCommentLikeRepository commentLikeRepository;
    @Mock private CommunityImageRepository imageRepository;
    @Mock private CommunityPostDeletionProcessor postDeletionProcessor;

    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PrimaryRegionResolver primaryRegionResolver;

    // ──────────────────── Helpers ────────────────────

    private Account createAccount(Long accountId, Long primaryRegionId) {
        Account account = Account.createUser("user@test.com", "pw", "이름", "닉네임", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", accountId);
        if (primaryRegionId != null) {
            account.setPrimaryRegion(primaryRegionId);
        }
        return account;
    }

    private Category createCategory(Long categoryId) {
        Category category = Category.createRoot(CategoryType.COMMUNITY, "커뮤니티", 1);
        ReflectionTestUtils.setField(category, "categoryId", categoryId);
        return category;
    }

    private Region createRegion(Long regionId) {
        Region region = Region.create("11000", "서울특별시", "강남구", "역삼동", 1000);
        ReflectionTestUtils.setField(region, "regionId", regionId);
        return region;
    }

    private CommunityPost createPost(Long postId, Account account, Long regionId) {
        Category category = createCategory(1L);
        Region region = createRegion(regionId);
        CommunityPost post = CommunityPost.create(account, category, region, "테스트 제목", "테스트 내용");
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private AccountRegion createAccountRegion(Long accountRegionId, Account account, Region region, boolean verified) {
        AccountRegion accountRegion = AccountRegion.create(account, region);
        ReflectionTestUtils.setField(accountRegion, "accountRegionId", accountRegionId);
        if (verified) {
            accountRegion.verify();
        }
        return accountRegion;
    }

    // 대표 지역 판정은 PrimaryRegionResolver에 있다(PrimaryRegionResolverTest에서 검증).
    // 여기서는 그 결과에 따른 커뮤니티 동작만 본다.
    private void stubVerifiedPrimaryRegion(Long accountId, Long accountRegionId, Account account, Long regionId) {
        when(primaryRegionResolver.resolve(any())).thenReturn(createRegion(regionId));
    }

    private void stubPrimaryRegionFailure(ErrorCode errorCode) {
        when(primaryRegionResolver.resolve(any())).thenThrow(new BusinessException(errorCode));
    }

    // ──────────────────── getPosts ────────────────────

    @Test
    void 게시글_목록_조회_성공() {
        // given
        Long accountId = 1L;
        Long regionId = 100L;
        Pageable pageable = PageRequest.of(0, 10);

        Account account = createAccount(accountId, regionId);
        CommunityPost post = createPost(10L, account, regionId);
        CommunityImage thumbnail = CommunityImage.create(post, "thumb.jpg", 0);

        Page<CommunityPost> postPage = new PageImpl<>(List.of(post), pageable, 1);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubVerifiedPrimaryRegion(accountId, regionId, account, regionId);
        when(postRepository.searchByRegionAndKeyword(regionId, null, pageable)).thenReturn(postPage);
        when(postLikeRepository.findLikedPostIds(accountId, List.of(10L))).thenReturn(Set.of(10L));

        // when
        Page<CommunityPostSummaryResponseDto> result = postService.getPosts(accountId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPostId()).isEqualTo(10L);
        assertThat(result.getContent().get(0).isLikedByMe()).isTrue();
    }

    @Test
    void 게시글_목록_조회_결과_없으면_썸네일_좋아요_조회_생략() {
        // given
        Long accountId = 1L;
        Long regionId = 100L;
        Pageable pageable = PageRequest.of(0, 10);
        Account account = createAccount(accountId, regionId);

        Page<CommunityPost> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubVerifiedPrimaryRegion(accountId, regionId, account, regionId);
        when(postRepository.searchByRegionAndKeyword(regionId, null, pageable)).thenReturn(emptyPage);

        // when
        Page<CommunityPostSummaryResponseDto> result = postService.getPosts(accountId, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        verify(postLikeRepository, never()).findLikedPostIds(any(), any());
    }

    @Test
    void 게시글_목록_조회_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.getPosts(accountId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(postRepository, never()).searchByRegionAndKeyword(any(), any(), any());
    }

    @Test
    void 게시글_목록_조회_대표지역_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, null);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPrimaryRegionFailure(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        // when & then
        assertThatThrownBy(() -> postService.getPosts(accountId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        verify(postRepository, never()).searchByRegionAndKeyword(any(), any(), any());
    }

    @Test
    void 게시글_목록_조회_대표지역_정보_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long accountRegionId = 100L;
        Account account = createAccount(accountId, accountRegionId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPrimaryRegionFailure(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        // when & then
        assertThatThrownBy(() -> postService.getPosts(accountId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        verify(postRepository, never()).searchByRegionAndKeyword(any(), any(), any());
    }

    @Test
    void 게시글_목록_조회_지역_미인증이면_REGION_NOT_VERIFIED() {
        // given
        Long accountId = 1L;
        Long accountRegionId = 100L;
        Account account = createAccount(accountId, accountRegionId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPrimaryRegionFailure(ErrorCode.REGION_NOT_VERIFIED);

        // when & then
        assertThatThrownBy(() -> postService.getPosts(accountId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);

        verify(postRepository, never()).searchByRegionAndKeyword(any(), any(), any());
    }

    // ──────────────────── getPost ────────────────────

    @Test
    void 게시글_상세_조회_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Long regionId = 100L;
        Account account = createAccount(accountId, regionId);
        CommunityPost post = createPost(postId, account, regionId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.increaseViewCountIfVisible(postId)).thenReturn(1);
        stubVerifiedPrimaryRegion(accountId, regionId, account, regionId);
        when(imageRepository.findByPost_PostIdOrderByDisplayOrder(postId)).thenReturn(List.of());
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(true);

        // when
        CommunityPostDetailResponseDto result = postService.getPost(accountId, postId);

        // then
        assertThat(result.isLikedByMe()).isTrue();
        verify(postRepository).increaseViewCountIfVisible(postId);
    }

    @Test
    void 지역_검증_후_게시글이_숨김되면_조회수를_올리지_않고_NOT_FOUND() {
        Long accountId = 1L;
        Long postId = 10L;
        Long regionId = 100L;
        Account account = createAccount(accountId, regionId);
        CommunityPost post = createPost(postId, account, regionId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.increaseViewCountIfVisible(postId)).thenReturn(0);
        stubVerifiedPrimaryRegion(accountId, regionId, account, regionId);

        assertThatThrownBy(() -> postService.getPost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);

        verify(imageRepository, never()).findByPost_PostIdOrderByDisplayOrder(postId);
    }

    @Test
    void 게시글_상세_조회_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.getPost(accountId, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 게시글_상세_조회_게시글_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.getPost(accountId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 게시글_상세_조회_다른_지역이면_없는_글로_응답한다() {
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
        assertThatThrownBy(() -> postService.getPost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 게시글_상세_조회_대표지역_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, null);
        CommunityPost post = createPost(postId, account, 100L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPrimaryRegionFailure(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> postService.getPost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void 게시글_상세_조회_지역_미인증이면_REGION_NOT_VERIFIED() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Long accountRegionId = 100L;
        Account account = createAccount(accountId, accountRegionId);
        CommunityPost post = createPost(postId, account, 200L);
        Region unverifiedRegion = createRegion(200L);
        AccountRegion accountRegion = createAccountRegion(accountRegionId, account, unverifiedRegion, false);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        stubPrimaryRegionFailure(ErrorCode.REGION_NOT_VERIFIED);

        // when & then
        assertThatThrownBy(() -> postService.getPost(accountId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);
    }

    // ──────────────────── createPost ────────────────────

    @Test
    void 게시글_작성_성공() {
        // given
        Long accountId = 1L;
        Long regionId = 100L;
        Account account = createAccount(accountId, regionId);
        Category category = createCategory(1L);

        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 1L);
        ReflectionTestUtils.setField(request, "title", "새 게시글");
        ReflectionTestUtils.setField(request, "content", "내용");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(1L, CategoryType.COMMUNITY))
                .thenReturn(Optional.of(category));
        stubVerifiedPrimaryRegion(accountId, regionId, account, regionId);
        when(postRepository.save(any(CommunityPost.class))).thenAnswer(inv -> {
            CommunityPost saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 10L);
            return saved;
        });

        // when
        CommunityPostDetailResponseDto result = postService.createPost(accountId, request);

        // then
        assertThat(result.getTitle()).isEqualTo("새 게시글");
        assertThat(result.isLikedByMe()).isFalse();
        verify(postRepository).save(any(CommunityPost.class));
    }

    @Test
    void 게시글_작성_계정_없으면_ACCOUNT_NOT_FOUND() {
        // given
        Long accountId = 999L;
        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.createPost(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void 게시글_작성_카테고리_없으면_CATEGORY_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, 100L);

        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 999L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(999L, CategoryType.COMMUNITY))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.createPost(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void 게시글_작성_대표지역_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Account account = createAccount(accountId, null);
        Category category = createCategory(1L);

        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 1L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        stubPrimaryRegionFailure(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(1L, CategoryType.COMMUNITY))
                .thenReturn(Optional.of(category));

        // when & then
        assertThatThrownBy(() -> postService.createPost(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void 게시글_작성_AccountRegion_없으면_ACCOUNT_PRIMARY_REGION_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long regionId = 100L;
        Account account = createAccount(accountId, regionId);
        Category category = createCategory(1L);

        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 1L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(1L, CategoryType.COMMUNITY))
                .thenReturn(Optional.of(category));
        stubPrimaryRegionFailure(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        // when & then
        assertThatThrownBy(() -> postService.createPost(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
    }

    @Test
    void 게시글_작성_지역_미인증이면_REGION_NOT_VERIFIED() {
        // given
        Long accountId = 1L;
        Long regionId = 100L;
        Account account = createAccount(accountId, regionId);
        Category category = createCategory(1L);
        Region region = createRegion(200L);
        AccountRegion accountRegion = createAccountRegion(regionId, account, region, false);

        CommunityPostCreateRequestDto request = new CommunityPostCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 1L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(1L, CategoryType.COMMUNITY))
                .thenReturn(Optional.of(category));
        stubPrimaryRegionFailure(ErrorCode.REGION_NOT_VERIFIED);

        // when & then
        assertThatThrownBy(() -> postService.createPost(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);
    }

    // ──────────────────── updatePost ────────────────────

    @Test
    void 게시글_수정_성공() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);
        Category category = createCategory(2L);

        CommunityPostUpdateRequestDto request = new CommunityPostUpdateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 2L);
        ReflectionTestUtils.setField(request, "title", "수정 제목");
        ReflectionTestUtils.setField(request, "content", "수정 내용");

        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(2L, CategoryType.COMMUNITY))
                .thenReturn(Optional.of(category));
        when(postLikeRepository.existsByAccount_AccountIdAndPost_PostId(accountId, postId)).thenReturn(false);

        // when
        CommunityPostDetailResponseDto result = postService.updatePost(accountId, postId, request);

        // then
        assertThat(result.getTitle()).isEqualTo("수정 제목");
        assertThat(result.getContent()).isEqualTo("수정 내용");
    }

    @Test
    void 게시글_수정_다른_사용자면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long otherId = 2L;
        Long postId = 10L;
        Account owner = createAccount(ownerId, 100L);
        CommunityPost post = createPost(postId, owner, 100L);

        CommunityPostUpdateRequestDto request = new CommunityPostUpdateRequestDto();

        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> postService.updatePost(otherId, postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
    }

    @Test
    void 게시글_수정_게시글_없으면_COMMUNITY_POST_NOT_FOUND() {
        // given
        when(postRepository.findVisibleByPostIdForUpdate(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.updatePost(1L, 999L, new CommunityPostUpdateRequestDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    @Test
    void 게시글_수정_카테고리_없으면_CATEGORY_NOT_FOUND() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        CommunityPostUpdateRequestDto request = new CommunityPostUpdateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", 999L);

        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(999L, CategoryType.COMMUNITY))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.updatePost(accountId, postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    // ──────────────────── deletePost ────────────────────

    @Test
    void 게시글_삭제_성공_관련_데이터_전부_삭제() {
        // given
        Long accountId = 1L;
        Long postId = 10L;
        Account account = createAccount(accountId, 100L);
        CommunityPost post = createPost(postId, account, 100L);

        when(postRepository.findWithAccountByPostIdForUpdate(postId)).thenReturn(Optional.of(post));

        // when
        postService.deletePost(accountId, postId);

        // then
        verify(postRepository).findWithAccountByPostIdForUpdate(postId);
        verify(postDeletionProcessor).deleteLockedPost(post);
    }

    @Test
    void 게시글_삭제_다른_사용자면_COMMUNITY_POST_ACCESS_DENIED() {
        // given
        Long ownerId = 1L;
        Long otherId = 2L;
        Long postId = 10L;
        Account owner = createAccount(ownerId, 100L);
        CommunityPost post = createPost(postId, owner, 100L);

        when(postRepository.findWithAccountByPostIdForUpdate(postId)).thenReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> postService.deletePost(otherId, postId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);

        verify(postDeletionProcessor, never()).deleteLockedPost(any());
    }

    @Test
    void 게시글_삭제_없는_게시글_COMMUNITY_POST_NOT_FOUND() {
        // given
        when(postRepository.findWithAccountByPostIdForUpdate(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.deletePost(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }
}
