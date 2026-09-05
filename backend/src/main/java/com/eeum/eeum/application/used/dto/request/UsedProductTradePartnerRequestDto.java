package com.eeum.eeum.application.used.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

/**
 * 예약·판매완료 시 거래 상대 지정.
 *
 * <p>{@code buyerId}는 선택이다. 앱 밖에서 성사된 거래를 정리하거나 상대 없이 "예약중"만
 * 표시하는 경우가 있어 강제하지 않는다. 다만 <b>후기는 구매자가 지정된 거래에만 붙는다.</b>
 */
@Getter
@Schema(description = "중고 거래 상대 지정 요청")
public class UsedProductTradePartnerRequestDto {

    @Positive
    @Schema(description = "거래 상대(구매자) 계정 ID. 생략하면 상대 없이 상태만 바꾼다", example = "17")
    private Long buyerId;
}
