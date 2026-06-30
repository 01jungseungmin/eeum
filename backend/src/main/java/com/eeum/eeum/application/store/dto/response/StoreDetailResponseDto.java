package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "사용자용 상점 상세 응답")
public class StoreDetailResponseDto {

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
    private List<StoreBusinessHourResponseDto> businessHours;

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

    @Schema(description = "상점 이미지 목록")
    private List<ImageResponseDto> images;

    @Schema(description = "상점 공지 목록")
    private List<StoreNoticeResponseDto> notices;

    @Schema(description = "상점 채팅방 ID", example = "12")
    private Long chatRoomId;

    @Schema(description = "채팅방 존재 여부", example = "true")
    private boolean chatRoomExists;

    @Schema(description = "현재 사용자의 채팅방 참여 여부", example = "false")
    private boolean joinedChatRoom;

    public static StoreDetailResponseDto of(
            Store store,
            List<ImageResponseDto> images,
            List<StoreNoticeResponseDto> notices,
            List<StoreBusinessHourResponseDto> businessHours,
            Long chatRoomId
    ) {
        return StoreDetailResponseDto.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .description(store.getDescription())
                .businessHours(businessHours)
                .status(store.getStatus().name())
                .rating(store.getRating())
                .favoriteCount(store.getFavoriteCount())
                .reviewCount(store.getReviewCount())
                .categoryId(store.getCategory() != null ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .chatRoomId(chatRoomId)
                .images(images)
                .notices(notices)
                .build();
    }
}