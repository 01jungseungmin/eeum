package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "상점 리뷰 답글 작성/수정 요청")
public class StoreReviewReplyRequestDto {

    @NotBlank(message = "답글 내용은 필수입니다.")
    @Size(max = 500, message = "답글 내용은 500자 이하로 입력해야 합니다.")
    @Schema(description = "답글 내용", example = "소중한 리뷰 감사합니다! 다음에 또 방문해주세요 :)")
    private String content;
}