package com.eeum.eeum.application.used.dto.request;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Schema(description = "중고 게시글 등록 요청")
public class UsedProductCreateRequestDto {

    @NotNull
    @Schema(description = "중고거래 카테고리 ID")
    private Long categoryId;

    // 활동 지역은 최대 2개라 어느 동네로 올릴지 사용자가 고른다.
    @NotNull
    @Schema(description = "거래 희망 지역 ID (본인의 인증된 활동 지역이어야 함)")
    private Long regionId;

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
