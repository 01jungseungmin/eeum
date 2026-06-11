package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "입점 심사용 상점 기본 영업 정보 입력 요청")
public class StoreBusinessInfoRequestDto {

    @NotNull(message = "업종은 필수입니다.")
    @Schema(description = "상점 업종 카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "상점 설명", example = "직접 끓인 김치찌개를 판매하는 동네 가게입니다.")
    private String description;
}