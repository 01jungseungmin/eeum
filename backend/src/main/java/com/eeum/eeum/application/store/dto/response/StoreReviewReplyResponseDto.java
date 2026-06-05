package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.entity.StoreReviewReply;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상점 리뷰 답글 응답")
public class StoreReviewReplyResponseDto {

    @Schema(description = "답글 ID", example = "1")
    private Long storereplyId;

    @Schema(description = "답글 작성자 ID (사장 account ID)", example = "7")
    private Long accountId;

    @Schema(description = "답글 작성자 닉네임", example = "승민반찬가게")
    private String nickname;

    @Schema(description = "답글 내용", example = "소중한 리뷰 감사합니다!")
    private String content;

    @Schema(description = "답글 작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "답글 수정일시")
    private LocalDateTime modifiedAt;

    public static StoreReviewReplyResponseDto from(StoreReviewReply reply) {
        return StoreReviewReplyResponseDto.builder()
                .storereplyId(reply.getStorereplyId())
                .accountId(reply.getAccount().getAccountId())
                .nickname(reply.getAccount().getNickname())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .modifiedAt(reply.getModifiedAt())
                .build();
    }
}