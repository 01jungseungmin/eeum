package com.eeum.eeum.application.used.dto.request;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Schema(description = "중고 게시글 등록 요청")
public class UsedProductCreateRequestDto {

    @NotNull
    @Positive
    @Schema(description = "중고거래 카테고리 ID")
    private Long categoryId;

    @Positive
    @Schema(description = "거래 희망 지역 ID. 생략하면 내가 선택한 동네(대표 지역)에 등록된다")
    private Long regionId;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "제목")
    private String title;

    @NotBlank
    @Size(max = 5000)
    @Schema(description = "본문")
    private String content;

    @NotNull
    @Schema(description = "거래 유형", allowableValues = {"FIXED", "FREE", "NEGOTIABLE"})
    private UsedProductPriceType priceType;

    // 컬럼이 DECIMAL(10,2)다. 검증이 없으면 자릿수 초과는 DB 경계에서 터지고,
    // 소수점 3자리 이하는 조용히 반올림되어 사용자가 입력한 값과 저장된 값이 달라진다.
    @DecimalMin(value = "0.00", message = "가격은 0 이상이어야 합니다.")
    @Digits(integer = 8, fraction = 2, message = "가격은 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
    @Schema(description = "가격. FIXED는 0보다 큰 값, FREE는 0, NEGOTIABLE은 보내지 않는다")
    private BigDecimal price;

    // ===== 거래 장소 (선택) =====
    // 장소명·위도·경도는 셋 다 보내거나 셋 다 생략한다. 부분 입력은 400이다.
    // 전부 생략하면 regionId가 담당하는 "동네만 지정"이 된다.

    @Size(max = 255)
    @Schema(description = "표시용 거래 장소명. 좌표와 함께 보낸다", example = "역삼동 주민센터 앞")
    private String tradeLocationName;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(description = "거래 장소 위도", example = "37.500123")
    private Double tradeLatitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(description = "거래 장소 경도", example = "127.036456")
    private Double tradeLongitude;

    @Size(max = 50)
    @Schema(description = "카카오 장소 ID. 지도에서 직접 찍은 핀이면 생략한다", example = "26338954")
    private String tradePlaceId;
}
