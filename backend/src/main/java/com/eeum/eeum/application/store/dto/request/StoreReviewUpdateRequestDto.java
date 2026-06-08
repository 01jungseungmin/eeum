package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
@Schema(description = "상점 리뷰 수정 요청")
public class StoreReviewUpdateRequestDto {

    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 최소 1점입니다.")
    @Max(value = 5, message = "별점은 최대 5점입니다.")
    @Schema(description = "수정할 별점 (1~5)", example = "4")
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(max = 1000, message = "리뷰 내용은 1000자 이하로 입력해야 합니다.")
    @Schema(description = "수정할 리뷰 내용", example = "재방문했는데 역시 맛있어요!")
    private String content;
}