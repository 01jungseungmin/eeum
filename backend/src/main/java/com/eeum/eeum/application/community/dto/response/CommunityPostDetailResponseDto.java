package com.eeum.eeum.application.community.dto.response;

import com.eeum.eeum.domain.community.entity.CommunityImage;
import com.eeum.eeum.domain.community.entity.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 상세 응답")
public class CommunityPostDetailResponseDto {

    @Schema(description = "게시글 ID")
    private Long postId;

    @Schema(description = "작성자 ID")
    private Long authorId;

    @Schema(description = "작성자 닉네임")
    private String authorNickname;

    @Schema(description = "작성자 프로필 이미지 URL")
    private String authorProfileImageUrl;

    @Schema(description = "카테고리 ID")
    private Long categoryId;

    @Schema(description = "카테고리 이름")
    private String categoryName;

    @Schema(description = "지역 ID")
    private Long regionId;

    @Schema(description = "지역명 (시도 구군 동)")
    private String regionName;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "내용")
    private String content;

    @Schema(description = "조회수")
    private int viewCount;

    @Schema(description = "좋아요 수")
    private int likeCount;

    @Schema(description = "댓글 수")
    private int commentCount;

    @Schema(description = "현재 로그인 사용자의 좋아요 여부")
    private boolean likedByMe;

    @Schema(description = "이미지 목록")
    private List<CommunityImageResponseDto> images;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private LocalDateTime modifiedAt;

    @Schema(description = "공유 딥링크 URL", example = "eeum://community/posts/42")
    private String shareUrl;

    public static CommunityPostDetailResponseDto of(CommunityPost post, boolean likedByMe, List<CommunityImage> images) {
        return CommunityPostDetailResponseDto.builder()
                .postId(post.getPostId())
                .authorId(post.getAccount().getAccountId())
                .authorNickname(post.getAccount().getNickname())
                .authorProfileImageUrl(post.getAccount().getProfileImageUrl())
                .categoryId(post.getCategory().getCategoryId())
                .categoryName(post.getCategory().getName())
                .regionId(post.getRegion().getRegionId())
                .regionName(post.getRegion().getFullName())
                .title(post.getTitle())
                .content(post.getContent())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByMe(likedByMe)
                .images(images.stream().map(CommunityImageResponseDto::from).toList())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .shareUrl("eeum://community/posts/" + post.getPostId())
                .build();
    }
}
