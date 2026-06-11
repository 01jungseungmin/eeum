package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "상점 리뷰 응답 (목록/작성/수정)")
public class StoreReviewResponseDto {

    @Schema(description = "리뷰 ID", example = "1")
    private Long storereviewId;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "작성자 account ID", example = "10")
    private Long accountId;

    @Schema(description = "작성자 닉네임", example = "동네주민A")
    private String nickname;

    @Schema(description = "별점 (1~5)", example = "5")
    private int rating;

    @Schema(description = "리뷰 내용", example = "음식이 정말 맛있었어요!")
    private String content;

    @Schema(description = "리뷰 이미지 목록")
    private List<ImageResponseDto> images;

    @Schema(description = "사장 답글 (없으면 null)")
    private StoreReviewReplyResponseDto reply;

    @Schema(description = "리뷰 작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "리뷰 수정일시")
    private LocalDateTime modifiedAt;

    // ===================== 정적 변환 메서드 =====================

    public static StoreReviewResponseDto of(
            StoreReview review,
            List<StoreReviewImage> images,
            StoreReviewReplyResponseDto reply
    ) {
        List<ImageResponseDto> imageDtos = images.stream()
                .map(img -> ImageResponseDto.builder()
                        .imageId(img.getStorereviewimageId())
                        .imageUrl(img.getImageUrl())
                        .displayOrder(img.getDisplayOrder())
                        .isThumbnail(img.isThumbnail())
                        .build())
                .toList();

        return StoreReviewResponseDto.builder()
                .storereviewId(review.getStorereviewId())
                .storeId(review.getStore().getStoreId())
                .accountId(review.getAccount().getAccountId())
                .nickname(review.getAccount().getNickname())
                .rating(review.getRating())
                .content(review.getContent())
                .images(imageDtos)
                .reply(reply)
                .createdAt(review.getCreatedAt())
                .modifiedAt(review.getModifiedAt())
                .build();
    }
}