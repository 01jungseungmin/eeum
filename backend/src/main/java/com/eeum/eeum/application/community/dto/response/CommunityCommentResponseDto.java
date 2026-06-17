package com.eeum.eeum.application.community.dto.response;

import com.eeum.eeum.domain.community.entity.CommunityComment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "댓글/대댓글 응답")
public class CommunityCommentResponseDto {

    @Schema(description = "댓글 ID")
    private Long commentId;

    @Schema(description = "작성자 ID")
    private Long authorId;

    @Schema(description = "작성자 닉네임")
    private String authorNickname;

    @Schema(description = "작성자 프로필 이미지 URL")
    private String authorProfileImageUrl;

    @Schema(description = "내용 (삭제된 경우 '삭제된 댓글입니다.')")
    private String content;

    @Schema(description = "좋아요 수")
    private int likeCount;

    @Schema(description = "현재 로그인 사용자의 좋아요 여부")
    private boolean likedByMe;

    @Schema(description = "대댓글 여부")
    private boolean reply;

    @Schema(description = "부모 댓글 ID (대댓글인 경우)")
    private Long parentCommentId;

    @Schema(description = "삭제 여부")
    private boolean deleted;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private LocalDateTime modifiedAt;

    public static CommunityCommentResponseDto of(CommunityComment comment, boolean likedByMe) {
        return CommunityCommentResponseDto.builder()
                .commentId(comment.getCommentId())
                .authorId(comment.getAccount().getAccountId())
                .authorNickname(comment.getAccount().getNickname())
                .authorProfileImageUrl(comment.getAccount().getProfileImageUrl())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .likedByMe(likedByMe)
                .reply(comment.isReply())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getCommentId() : null)
                .deleted(comment.isDeleted())
                .createdAt(comment.getCreatedAt())
                .modifiedAt(comment.getModifiedAt())
                .build();
    }
}
