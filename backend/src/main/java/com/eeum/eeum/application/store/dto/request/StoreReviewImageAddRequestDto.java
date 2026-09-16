package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "상점 리뷰 이미지 추가 요청")
public class StoreReviewImageAddRequestDto {

    @NotEmpty(message = "이미지 URL은 최소 1개 이상이어야 합니다.")
    @Size(max = 10, message = "이미지는 최대 10장까지 추가할 수 있습니다.")
    @Schema(description = "추가할 리뷰 이미지 URL 목록")
    private List<@NotBlank(message = "이미지 URL은 공백일 수 없습니다.") String> imageUrls;
}