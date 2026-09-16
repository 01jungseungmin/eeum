package com.eeum.eeum.application.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "이벤트 상품 등록/수정 요청")
public class EventProductRequestDto {

    @Schema(description = "이벤트로 등록할 상품 ID", example = "1")
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Schema(description = "이벤트 가격", example = "8900")
    @NotNull(message = "이벤트 가격은 필수입니다.")
    @PositiveOrZero(message = "이벤트 가격은 0원 이상이어야 합니다.")
    private Integer eventPrice;

    @Schema(description = "이벤트 판매 수량", example = "30")
    @NotNull(message = "이벤트 수량은 필수입니다.")
    @Positive(message = "이벤트 수량은 1개 이상이어야 합니다.")
    private Integer eventStock;

    @Schema(description = "이벤트 시작 일시", example = "2026-05-25T12:00:00")
    @NotNull(message = "이벤트 시작 일시는 필수입니다.")
    private LocalDateTime startAt;

    @Schema(description = "이벤트 종료 일시", example = "2026-05-25T18:00:00")
    @NotNull(message = "이벤트 종료 일시는 필수입니다.")
    private LocalDateTime endAt;
}