package com.eeum.eeum.application.community.dto.response;

import com.eeum.eeum.domain.community.entity.CommunityPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 목록 응답")
public class CommunityPostSummaryResponseDto {

    @Schema(description = "게시글 ID")
    private Long postId;

    @Schema(description = "작성자 ID")
    private Long authorId;

    @Schema(description = "작성자 닉네임")
    private String authorNickname;

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

    @Schema(description = "조회수")
    private int viewCount;

    @Schema(description = "좋아요 수")
    private int likeCount;

    @Schema(description = "댓글 수")
    private int commentCount;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "현재 로그인 사용자의 좋아요 여부")
    private boolean likedByMe;

    @Schema(description = "관리자 숨김 여부")
    private boolean hidden;

    public static CommunityPostSummaryResponseDto from(CommunityPost post, boolean likedByMe) {
        return CommunityPostSummaryResponseDto.builder()
                .postId(post.getPostId())
                .authorId(post.getAccount().getAccountId())
                .authorNickname(post.getAccount().getNickname())
                .categoryId(post.getCategory().getCategoryId())
                .categoryName(post.getCategory().getName())
                .regionId(post.getRegion().getRegionId())
                .regionName(post.getRegion().getFullName())
                .title(post.getTitle())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByMe(likedByMe)
                .hidden(post.isHidden())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
