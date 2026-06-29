package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.request.CommunityPostCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityPostUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
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
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final CommunityImageRepository imageRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRegionRepository accountRegionRepository;

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(Long accountId, Pageable pageable) {
        return getPosts(accountId, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(Long accountId, String keyword, Pageable pageable) {
        Account account = getAccountOrThrow(accountId);
        Region region = getPrimaryRegion(account);

        Page<CommunityPost> posts = postRepository.searchByRegionAndKeyword(
                region.getRegionId(),
                normalizeKeyword(keyword),
                pageable
        );

        return toSummaryPage(accountId, posts);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getMyPosts(Long accountId, Pageable pageable) {
        Page<CommunityPost> posts = postRepository.findByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable);
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

        postRepository.increaseViewCount(postId);  // clearAutomatically = true → 캐시 초기화
        post = getPostOrThrow(postId);              // 최신 viewCount 반영된 엔티티 재조회

        List<CommunityImage> images = imageRepository.findByPost_PostIdOrderByDisplayOrder(postId);

        boolean likedByMe = postLikeRepository
                .existsByAccount_AccountIdAndPost_PostId(accountId, postId);

        return CommunityPostDetailResponseDto.of(post, likedByMe, images);
    }

    @Transactional
    public CommunityPostDetailResponseDto createPost(Long accountId, CommunityPostCreateRequestDto request) {
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

        return CommunityPostDetailResponseDto.of(post, false, List.of());
    }

    @Transactional
    public CommunityPostDetailResponseDto updatePost(
            Long accountId,
            Long postId,
            CommunityPostUpdateRequestDto request
    ) {
        CommunityPost post = getPostOrThrow(postId);
        validateOwner(post, accountId);

        Category category = getCategoryOrThrow(request.getCategoryId());
        post.update(category, request.getTitle(), request.getContent());

        boolean likedByMe = postLikeRepository
                .existsByAccount_AccountIdAndPost_PostId(accountId, postId);

        List<CommunityImage> images = imageRepository.findByPost_PostIdOrderByDisplayOrder(postId);

        return CommunityPostDetailResponseDto.of(post, likedByMe, images);
    }

    @Transactional
    public void deletePost(Long accountId, Long postId) {
        CommunityPost post = getPostOrThrow(postId);
        validateOwner(post, accountId);

        commentLikeRepository.deleteByComment_Post_PostId(postId);
        commentRepository.deleteRepliesByPost_PostId(postId);
        commentRepository.deleteTopLevelCommentsByPost_PostId(postId);
        postLikeRepository.deleteByPost_PostId(postId);
        imageRepository.deleteByPost_PostId(postId);
        postRepository.delete(post);

        log.info("커뮤니티 게시글 삭제: accountId={}, postId={}", accountId, postId);
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
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
        Long primaryAccountRegionId = account.getPrimaryRegionId();

        if (primaryAccountRegionId == null) throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);

        AccountRegion accountRegion = accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(
                        primaryAccountRegionId,
                        account.getAccountId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND));

        if (!accountRegion.isVerified()) {
            throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
        }

        return accountRegion.getRegion();
    }

    private void validateOwner(CommunityPost post, Long accountId) {
        if (!post.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }

    private void validateSameRegion(CommunityPost post, Account account) {
        Region myRegion = getPrimaryRegion(account);

        if (!post.getRegion().getRegionId().equals(myRegion.getRegionId())) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
    }
}