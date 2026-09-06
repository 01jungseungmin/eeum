package com.eeum.eeum.application.community.service;

import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.request.ImageUploadRequestDto;
import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostImageServiceTest {

    @InjectMocks private CommunityPostImageService service;
    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityImageRepository imageRepository;
    @Mock private com.eeum.eeum.application.file.FileStorageService fileStorageService;

    @Test
    void 이미지_추가는_노출중인_게시글을_쓰기잠금으로_조회한다() {
        Long accountId = 1L;
        Long postId = 10L;
        CommunityPost post = ownedPost(accountId);
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        when(imageRepository.countByPost_PostId(postId)).thenReturn(0);
        when(imageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addImages(accountId, postId, imageRequest("https://example.com/image.jpg"));

        verify(postRepository).findVisibleByPostIdForUpdate(postId);
        verify(imageRepository).saveAll(anyList());
    }

    @Test
    void 이미지_개수_검증도_게시글_잠금_획득_후_수행한다() {
        Long accountId = 1L;
        Long postId = 10L;
        CommunityPost post = ownedPost(accountId);
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        when(imageRepository.countByPost_PostId(postId)).thenReturn(20);

        assertThatThrownBy(() -> service.addImages(
                accountId, postId, imageRequest("https://example.com/overflow.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(postRepository).findVisibleByPostIdForUpdate(postId);
        verify(imageRepository, never()).saveAll(anyList());
    }

    @Test
    void 숨김된_게시글에는_이미지를_추가하지_않는다() {
        Long postId = 10L;
        when(postRepository.findVisibleByPostIdForUpdate(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addImages(
                1L, postId, imageRequest("https://example.com/hidden.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);

        verify(imageRepository, never()).countByPost_PostId(postId);
        verify(imageRepository, never()).saveAll(anyList());
    }

    @Test
    void 이미지_삭제는_숨김여부와_무관한_게시글_쓰기잠금을_사용한다() {
        Long accountId = 1L;
        Long postId = 10L;
        Long imageId = 20L;
        CommunityPost post = ownedPost(accountId);
        when(post.getPostId()).thenReturn(postId);
        CommunityImage image = CommunityImage.create(post, "https://example.com/delete.jpg", 1);
        when(postRepository.findWithAccountByPostIdForUpdate(postId)).thenReturn(Optional.of(post));
        when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));
        when(imageRepository.findByPost_PostIdOrderByDisplayOrder(postId)).thenReturn(List.of());

        service.deleteImage(accountId, postId, imageId);

        verify(postRepository).findWithAccountByPostIdForUpdate(postId);
        verify(imageRepository).delete(image);
    }

    private CommunityPost ownedPost(Long accountId) {
        CommunityPost post = mock(CommunityPost.class);
        when(post.isOwnedBy(accountId)).thenReturn(true);
        return post;
    }

    private ImageUploadListRequestDto imageRequest(String url) {
        ImageUploadRequestDto image = new ImageUploadRequestDto();
        ReflectionTestUtils.setField(image, "imageUrl", url);
        ImageUploadListRequestDto request = new ImageUploadListRequestDto();
        ReflectionTestUtils.setField(request, "images", List.of(image));
        return request;
    }
}
