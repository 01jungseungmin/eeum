package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상점 찜 목록 응답 (상점 정보 포함)")
public class FavoriteStoreResponseDto {

    @Schema(description = "찜 ID", example = "10")
    private Long favoriteId;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상점명", example = "승민 반찬가게")
    private String name;

    @Schema(description = "상점 주소", example = "서울특별시 마포구 월드컵북로 10")
    private String address;

    @Schema(description = "상점 평점", example = "4.8")
    private Double rating;

    @Schema(description = "리뷰 수", example = "32")
    private Integer reviewCount;

    @Schema(description = "상점 상태", example = "OPEN")
    private String status;

    @Schema(description = "썸네일 이미지 URL", example = "https://example.com/store-thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "찜 등록 일시")
    private LocalDateTime favoritedAt;

    public static FavoriteStoreResponseDto of(Long favoriteId, Store store,
            String thumbnailUrl, LocalDateTime favoritedAt) {
        return FavoriteStoreResponseDto.builder()
                .favoriteId(favoriteId)
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .rating(store.getRating())
                .reviewCount(store.getReviewCount())
                .status(store.getStatus().name())
                .thumbnailUrl(thumbnailUrl)
                .favoritedAt(favoritedAt)
                .build();
    }
}