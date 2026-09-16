package com.eeum.eeum.application.used.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "중고거래 후기 수정 요청")
public class UsedReviewUpdateRequestDto {

    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 최소 1점입니다.")
    @Max(value = 5, message = "별점은 최대 5점입니다.")
    @Schema(description = "별점 (1~5)", example = "4")
    private Integer rating;

    @NotBlank(message = "후기 내용은 필수입니다.")
    @Size(max = 1000, message = "후기 내용은 1000자 이하로 입력해야 합니다.")
    @Schema(description = "후기 내용", example = "다시 보니 상태가 더 좋네요.")
    private String content;
}
