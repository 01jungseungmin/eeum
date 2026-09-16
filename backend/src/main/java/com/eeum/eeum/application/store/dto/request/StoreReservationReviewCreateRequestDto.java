package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "방문 예약 리뷰 작성 요청")
public class StoreReservationReviewCreateRequestDto {

    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 최소 1점입니다.")
    @Max(value = 5, message = "별점은 최대 5점입니다.")
    @Schema(description = "별점 (1~5)", example = "5")
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(max = 1000, message = "리뷰 내용은 1000자 이하로 입력해야 합니다.")
    @Schema(description = "리뷰 내용", example = "친절하고 좋았어요!")
    private String content;

    @Schema(description = "리뷰 이미지 URL 목록 (최대 10장)")
    @Size(max = 10, message = "이미지는 최대 10장까지 등록할 수 있습니다.")
    private List<String> imageUrls;
}
