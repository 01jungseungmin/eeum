package com.eeum.eeum.application.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상점 정보 응답")
public class StoreResponseDto {

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "상점명", example = "맛있는 반찬가게")
    private String name;

    @Schema(description = "상점 주소", example = "서울특별시 마포구 합정동 123-45")
    private String address;

    @Schema(description = "상점 전화번호", example = "02-1234-5678")
    private String phone;

    @Schema(description = "상점 설명", example = "신선한 재료로 매일 직접 만드는 반찬가게입니다.")
    private String description;

    @Schema(description = "영업시간", example = "월~토 09:00~19:00")
    private String businessHours;

    @Schema(description = "상점 상태", example = "OPEN")
    private String status;

    @Schema(description = "상점 평점", example = "4.8")
    private Double rating;

    @Schema(description = "찜/관심 고객 수", example = "89")
    private Integer favoriteCount;

    @Schema(description = "리뷰 수", example = "124")
    private Integer reviewCount;

    @Schema(description = "상점 업종 카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "상점 업종명", example = "반찬/가정식")
    private String categoryName;

    @Schema(description = "위도", example = "37.5665")
    private Double latitude;

    @Schema(description = "경도", example = "126.9780")
    private Double longitude;

    @Schema(description = "등록일시", example = "2026-05-25T12:00:00")
    private LocalDateTime createdAt;
}