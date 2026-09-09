package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.community.dto.request.CommunityPostCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityPostUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.application.account.service.PrimaryRegionResolver;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.application.category.service.CategoryQueryService;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private final FileStorageService fileStorageService;
    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityImageRepository imageRepository;
    private final CommunityPostDeletionProcessor postDeletionProcessor;
    private final AccountRepository accountRepository;
    private final AccountWriteGuard accountWriteGuard;
    private final CategoryRepository categoryRepository;
    private final CategoryQueryService categoryQueryService;
    private final PrimaryRegionResolver primaryRegionResolver;

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(Long accountId, Pageable pageable) {
        return getPosts(accountId, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(Long accountId, String keyword, Pageable pageable) {
        return getPosts(accountId, keyword, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(
            Long accountId,
            String keyword,
            Long categoryId,
            Pageable pageable
    ) {
        Account account = getAccountOrThrow(accountId);
        Region region = getPrimaryRegion(account);

        Page<CommunityPost> posts = categoryId == null
                ? postRepository.searchByRegionAndKeyword(region.getRegionId(), normalizeKeyword(keyword), pageable)
                : postRepository.searchByRegionKeywordAndCategoryIds(
                        region.getRegionId(),
                        normalizeKeyword(keyword),
                        categoryQueryService.resolveActiveCategoryIds(CategoryType.COMMUNITY, categoryId),
                        pageable);

        return toSummaryPage(accountId, posts);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getMyPosts(Long accountId, Pageable pageable) {
        Page<CommunityPost> posts = postRepository
                .findByAccount_AccountIdAndHiddenFalseOrderByCreatedAtDesc(accountId, pageable);
        return toSummaryPage(accountId, posts);
    }

    private Page<CommunityPostSummaryResponseDto> toSummaryPage(Long accountId, Page<CommunityPost> posts) {
        if (posts.isEmpty()) {
            return posts.map(post -> CommunityPostSummaryResponseDto.from(post, false));
        }

        List<Long> postIds = posts.stream()
                .map(CommunityPost::getPostId)
                .toList();

        Set<Long> likedPostIds = postLikeRepository.findLikedPostIds(accountId, postIds);

        return posts.map(post -> CommunityPostSummaryResponseDto.from(
                post,
                likedPostIds.contains(post.getPostId())
        ));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    @Transactional
    public CommunityPostDetailResponseDto getPost(Long accountId, Long postId) {
        Account account = getAccountOrThrow(accountId);
        CommunityPost post = getPostOrThrow(postId);

        validateSameRegion(post, account);

        int updatedRows = postRepository.increaseViewCountIfVisible(postId);
        if (updatedRows == 0) {
            throw new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        post = getPostOrThrow(postId);              // 최신 viewCount 반영된 엔티티 재조회

        List<CommunityImage> images = imageRepository.findByPost_PostIdOrderByDisplayOrder(postId);

        boolean likedByMe = postLikeRepository
                .existsByAccount_AccountIdAndPost_PostId(accountId, postId);

        return CommunityPostDetailResponseDto.of(post, likedByMe, images,
                CommunityImage::getImageUrl,
                java.util.function.Function.identity());
    }

    @Transactional
    public CommunityPostDetailResponseDto createPost(Long accountId, CommunityPostCreateRequestDto request) {
        // 계정 → 게시글 순서로 잠근다. 탈퇴·정지와 겹쳐도 이후 게시글이 남지 않는다.
        accountWriteGuard.lockActive(accountId);
        Account account = getAccountOrThrow(accountId);
        Category category = getCategoryOrThrow(request.getCategoryId());
        Region region = getPrimaryRegion(account);

        CommunityPost post = CommunityPost.create(
                account,
                category,
                region,
                request.getTitle(),
                request.getContent()
        );

        postRepository.save(post);

        log.info("커뮤니티 게시글 작성: accountId={}, postId={}", accountId, post.getPostId());

        return CommunityPostDetailResponseDto.of(post, false, List.of(),
                CommunityImage::getImageUrl,
                java.util.function.Function.identity());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommunityPostDetailResponseDto updatePost(
            Long accountId,
            Long postId,
            CommunityPostUpdateRequestDto request
    ) {
        // account → post 전역 순서를 지켜 탈퇴·정지 후 수정 커밋을 막는다.
        accountWriteGuard.lockActive(accountId);
        CommunityPost post = getVisiblePostForUpdateOrThrow(postId);
        validateOwner(post, accountId);

        Category category = getCategoryOrThrow(request.getCategoryId());
        post.update(category, request.getTitle(), request.getContent());

        boolean likedByMe = postLikeRepository
                .existsByAccount_AccountIdAndPost_PostId(accountId, postId);

        List<CommunityImage> images = imageRepository.findByPost_PostIdOrderByDisplayOrder(postId);

        return CommunityPostDetailResponseDto.of(post, likedByMe, images,
                CommunityImage::getImageUrl,
                java.util.function.Function.identity());
    }

    @Transactional
    public void deletePost(Long accountId, Long postId) {
        // 계정 상태를 먼저 잠근 뒤 게시글과 하위 데이터를 정리한다.
        accountWriteGuard.lockActive(accountId);
        CommunityPost post = getPostForUpdateOrThrow(postId);
        validateOwner(post, accountId);
        postDeletionProcessor.deleteLockedPost(post);

        log.info("커뮤니티 게시글 삭제: accountId={}, postId={}", accountId, postId);
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private CommunityPost getPostForUpdateOrThrow(Long postId) {
        return postRepository.findWithAccountByPostIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private CommunityPost getVisiblePostForUpdateOrThrow(Long postId) {
        return postRepository.findVisibleByPostIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private Account getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(categoryId, CategoryType.COMMUNITY)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Region getPrimaryRegion(Account account) {
        return primaryRegionResolver.resolve(account);
    }

    private void validateOwner(CommunityPost post, Long accountId) {
        if (!post.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }

    // 다른 동네 글은 없는 것으로 응답한다.
    // 403을 주면 없는 글(404)과 구분되어, ID를 넣어보는 것만으로 타 지역 글의 존재를 알 수 있다.
    private void validateSameRegion(CommunityPost post, Account account) {
        Region myRegion = getPrimaryRegion(account);

        if (!post.getRegion().getRegionId().equals(myRegion.getRegionId())) {
            throw new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
    }
}
