package com.eeum.eeum.application.community.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import com.eeum.eeum.domain.community.repository.CommunityImageRepository;
import com.eeum.eeum.domain.community.repository.CommunityPostRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCommunityPostServiceTest {

    private static final Long POST_ID = 10L;

    @Mock private CommunityPostRepository communityPostRepository;
    @Mock private CommunityImageRepository communityImageRepository;

    @InjectMocks
    private AdminCommunityPostService adminCommunityPostService;

    @Test
    void 관리자_숨김은_신고_조치와_같은_비관적_잠금으로_게시글을_읽는다() {
        // given
        CommunityPost post = post();
        when(communityPostRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when
        adminCommunityPostService.hide(POST_ID);

        // then
        assertThat(post.isHidden()).isTrue();
    }

    @Test
    void 숨김_게시글은_관리자가_다시_노출할_수_있다() {
        // given
        CommunityPost post = post();
        post.hide();
        when(communityPostRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when
        adminCommunityPostService.show(POST_ID);

        // then
        assertThat(post.isHidden()).isFalse();
    }

    @Test
    void 이미_숨김인_게시글은_다시_숨길_수_없다() {
        // given
        CommunityPost post = post();
        post.hide();
        when(communityPostRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> adminCommunityPostService.hide(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 존재하지_않는_게시글은_관리자_조치를_할_수_없다() {
        // given
        when(communityPostRepository.findWithAccountByPostIdForUpdate(POST_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminCommunityPostService.show(POST_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
    }

    private CommunityPost post() {
        Account author = Account.createUser(
                "author@test.com", "encoded-pw", "작성자", "작성자닉", "010-0000-0000");
        CommunityPost post = CommunityPost.create(
                author,
                Category.createRoot(CategoryType.COMMUNITY, "자유", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "제목", "내용");
        ReflectionTestUtils.setField(post, "postId", POST_ID);
        return post;
    }
}
