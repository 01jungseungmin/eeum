package com.eeum.eeum.application.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "사용자용 상점 목록 응답")
public class StoreListResponseDto {

    @Schema(description = "상점 ID", example = "1")
    private Long storeId;

    @Schema(description = "상점명", example = "승민 반찬가게")
    private String name;

    @Schema(description = "상점 주소", example = "서울특별시 마포구 월드컵북로 10")
    private String address;

    @Schema(description = "상점 전화번호", example = "02-1111-2222")
    private String phone;

    @Schema(description = "상점 설명", example = "매일 직접 만드는 수제 반찬 전문점입니다.")
    private String description;

    @Schema(description = "영업시간", example = "월~토 10:00~20:00")
    private String businessHours;

    @Schema(description = "상점 상태", example = "OPEN", allowableValues = {"OPEN", "TEMP_CLOSED", "CLOSED"})
    private String status;

    @Schema(description = "상점 평점", example = "4.8")
    private Double rating;

    @Schema(description = "찜/관심 고객 수", example = "128")
    private Integer favoriteCount;

    @Schema(description = "리뷰 수", example = "32")
    private Integer reviewCount;

    @Schema(description = "상점 업종 카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "상점 업종명", example = "반찬/도시락")
    private String categoryName;

    @Schema(description = "상점 위도", example = "37.5665")
    private Double latitude;

    @Schema(description = "상점 경도", example = "126.9780")
    private Double longitude;

    @Schema(description = "상점 대표 이미지 URL", example = "https://example.com/images/store-thumbnail.jpg")
    private String thumbnailUrl;
}