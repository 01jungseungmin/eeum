package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/** 관리자용 커뮤니티 게시글 조회 및 노출 상태 조치 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommunityPostService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityImageRepository communityImageRepository;

    @Transactional(readOnly = true)
    public Page<CommunityPostSummaryResponseDto> getPosts(Pageable pageable) {
        return communityPostRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(post -> CommunityPostSummaryResponseDto.from(post, false));
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponseDto getDetail(Long postId) {
        CommunityPost post = communityPostRepository.findWithAccountByPostId(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        List<CommunityImage> images = communityImageRepository.findByPost_PostIdOrderByDisplayOrder(postId);

        return CommunityPostDetailResponseDto.of(
                post, false, images, CommunityImage::getImageUrl, Function.identity());
    }

    @Transactional
    public void hide(Long postId) {
        CommunityPost post = getPostForUpdate(postId);
        if (post.isHidden()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        post.hide();
        log.info("커뮤니티 게시글 관리자 숨김: postId={}", postId);
    }

    @Transactional
    public void show(Long postId) {
        CommunityPost post = getPostForUpdate(postId);
        if (!post.isHidden()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        post.show();
        log.info("커뮤니티 게시글 관리자 숨김 해제: postId={}", postId);
    }

    private CommunityPost getPostForUpdate(Long postId) {
        return communityPostRepository.findWithAccountByPostIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }
}
