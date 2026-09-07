package com.eeum.eeum.application.community.service;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityCommentLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityCommentRepository;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostLikeRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CommunityPostDeletionProcessorTest {

    @InjectMocks private CommunityPostDeletionProcessor processor;

    @Mock private CommunityPostRepository postRepository;
    @Mock private CommunityPostLikeRepository postLikeRepository;
    @Mock private CommunityCommentRepository commentRepository;
    @Mock private CommunityCommentLikeRepository commentLikeRepository;
    @Mock private CommunityImageRepository imageRepository;
    @Mock private com.eeum.eeum.application.file.FileStorageService fileStorageService;

    @Test
    void 게시글_삭제시_첨부_객체도_정리_대상으로_전환한다() {
        // Given — 이미지 행만 삭제하면 FileObject가 ATTACHED로 남아 영구 누적된다.
        CommunityPost post = CommunityPost.create(null, null, null, "제목", "본문");
        ReflectionTestUtils.setField(post, "postId", 10L);
        var image = com.eeum.eeum.domain.community.entity.CommunityImage.create(post, "community/42/a.png", 1);
        org.mockito.BDDMockito.given(imageRepository.findByPost_PostIdOrderByDisplayOrder(10L))
                .willReturn(java.util.List.of(image));
        // When
        processor.deleteLockedPost(post);
        // Then
        org.mockito.Mockito.verify(fileStorageService).scheduleAttachedObjectCleanup("community/42/a.png");
    }

    @Test
    void 잠긴_게시글은_자식_FK_순서대로_삭제한다() {
        // Given
        CommunityPost post = CommunityPost.create(null, null, null, "제목", "본문");
        ReflectionTestUtils.setField(post, "postId", 10L);

        // When
        processor.deleteLockedPost(post);

        // Then
        InOrder order = inOrder(
                commentLikeRepository,
                commentRepository,
                postLikeRepository,
                imageRepository,
                postRepository
        );
        order.verify(commentLikeRepository).deleteByComment_Post_PostId(10L);
        order.verify(commentRepository).deleteRepliesByPost_PostId(10L);
        order.verify(commentRepository).deleteTopLevelCommentsByPost_PostId(10L);
        order.verify(postLikeRepository).deleteByPost_PostId(10L);
        order.verify(imageRepository).deleteByPost_PostId(10L);
        order.verify(postRepository).delete(post);
    }
}
