package com.eeum.eeum.application.community.service;

import com.eeum.eeum.application.community.dto.response.CommunityImageResponseDto;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityPostImageService {

    private static final int MAX_IMAGE_COUNT = 20;

    private final CommunityPostRepository postRepository;
    private final CommunityImageRepository imageRepository;

    @Transactional
    public List<CommunityImageResponseDto> addImages(Long accountId, Long postId, ImageUploadListRequestDto request) {
        CommunityPost post = getPostWithOwnerCheck(accountId, postId);

        int currentCount = imageRepository.countByPost_PostId(postId);
        if (currentCount + request.getImages().size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        List<CommunityImage> images = new ArrayList<>();
        for (int i = 0; i < request.getImages().size(); i++) {
            images.add(CommunityImage.create(
                    post,
                    request.getImages().get(i).getImageUrl(),
                    currentCount + i + 1
            ));
        }

        List<CommunityImage> saved = imageRepository.saveAll(images);
        log.info("커뮤니티 이미지 등록: postId={}, count={}", postId, saved.size());
        return saved.stream().map(CommunityImageResponseDto::from).toList();
    }

    @Transactional
    public void deleteImage(Long accountId, Long postId, Long imageId) {
        getPostWithOwnerCheck(accountId, postId);

        CommunityImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.IMAGE_NOT_FOUND));

        if (!image.getPost().getPostId().equals(postId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }

        imageRepository.delete(image);
        imageRepository.flush();

        List<CommunityImage> images = imageRepository.findByPost_PostIdOrderByDisplayOrder(postId);
        for (int i = 0; i < images.size(); i++) {
            images.get(i).changeDisplayOrder(i + 1);
        }
        log.info("커뮤니티 이미지 삭제: imageId={}", imageId);
    }

    private CommunityPost getPostWithOwnerCheck(Long accountId, Long postId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        if (!post.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.COMMUNITY_POST_ACCESS_DENIED);
        }
        return post;
    }
}
