package com.eeum.eeum.application.used.dto.request;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;

// 중고 게시글 수정 요청(거래 희망 지역 변경 x)
@Getter
@Schema(description = "중고 게시글 수정 요청")
public class UsedProductUpdateRequestDto {

    @NotNull
    @Schema(description = "중고거래 카테고리 ID")
    private Long categoryId;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "제목")
    private String title;

    @NotBlank
    @Schema(description = "본문")
    private String content;

    @NotNull
    @Schema(description = "거래 유형", allowableValues = {"FIXED", "FREE", "NEGOTIABLE"})
    private UsedProductPriceType priceType;

    @Schema(description = "가격. FIXED는 0보다 큰 값, FREE는 0, NEGOTIABLE은 보내지 않는다")
    private BigDecimal price;
}
