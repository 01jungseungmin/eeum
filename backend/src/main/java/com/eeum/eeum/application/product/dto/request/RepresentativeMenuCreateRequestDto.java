package com.eeum.eeum.application.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "대표 메뉴 등록 요청")
public class RepresentativeMenuCreateRequestDto {

    @Schema(description = "대표 메뉴명", example = "김치찌개")
    @NotBlank(message = "대표 메뉴명은 필수입니다.")
    @Size(max = 100, message = "대표 메뉴명은 100자 이하로 입력해야 합니다.")
    private String name;

    @Schema(description = "대표 메뉴 설명", example = "직접 끓인 김치찌개입니다.")
    @Size(max = 1000, message = "대표 메뉴 설명은 1000자 이하로 입력해야 합니다.")
    private String description;

    @Schema(description = "대표 메뉴 가격", example = "8000")
    @NotNull(message = "대표 메뉴 가격은 필수입니다.")
    @PositiveOrZero(message = "대표 메뉴 가격은 0원 이상이어야 합니다.")
    private Integer basePrice;
}