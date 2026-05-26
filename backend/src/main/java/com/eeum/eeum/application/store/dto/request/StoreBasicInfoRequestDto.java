package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "입점 심사용 상점 기본 정보 수정 요청")
public class StoreBasicInfoRequestDto {

    @NotBlank(message = "상점명은 필수입니다")
    private String name;

    @NotBlank(message = "상점 주소는 필수입니다")
    private String address;

    @NotBlank(message = "상점 전화번호는 필수입니다")
    private String phone;

    @NotNull(message = "업종은 필수입니다")
    private Long categoryId;

    @NotNull(message = "상점 지역은 필수입니다")
    private Long regionId;

    @NotNull(message = "위도는 필수입니다")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다")
    private Double longitude;

    private String description;

    @NotBlank(message = "영업시간은 필수입니다")
    private String businessHours;
}