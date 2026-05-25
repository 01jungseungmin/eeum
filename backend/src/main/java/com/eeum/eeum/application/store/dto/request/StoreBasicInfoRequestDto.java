package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "상점 기본 정보 수정 요청")
public class StoreBasicInfoRequestDto {

    @NotNull(message = "업종은 필수입니다")
    private Long categoryId;

    private String description;

    @NotBlank(message = "영업시간은 필수입니다")
    private String businessHours;
}