package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewImage;
import com.eeum.eeum.domain.store.enums.StoreReviewType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Getter
@Builder
@Schema(description = "내가 작성한 리뷰 응답 (주문 리뷰 / 예약 리뷰 공통)")
public class MyReviewResponseDto {

    @Schema(description = "리뷰 ID", example = "1")
    private Long storereviewId;

    @Schema(description = "리뷰 타입", example = "ORDER")
    private StoreReviewType reviewType;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상점명", example = "승민반찬가게")
    private String storeName;

    @Schema(description = "별점 (1~5)", example = "5")
    private int rating;

    @Schema(description = "리뷰 내용", example = "음식이 정말 맛있었어요!")
    private String content;

    @Schema(description = "리뷰 이미지 목록")
    private List<ImageResponseDto> images;

    @Schema(description = "주문 ID (주문 리뷰인 경우)")
    private Long orderId;

    @Schema(description = "주문 상품 요약 (주문 리뷰인 경우)")
    private List<OrderItemSummaryDto> orderItems;

    @Schema(description = "방문 예약 ID (예약 리뷰인 경우)")
    private Long visitReservationId;

    @Schema(description = "방문 예약일 (예약 리뷰인 경우)")
    private LocalDate visitDate;

    @Schema(description = "방문 예약시간 (예약 리뷰인 경우)")
    private LocalTime visitTime;

    @Schema(description = "리뷰 작성일시")
    private LocalDateTime createdAt;

    public static MyReviewResponseDto ofOrder(
            StoreReview review,
            List<StoreReviewImage> images,
            List<OrderItem> orderItems
    ) {
        return ofOrder(review, images, orderItems, Function.identity());
    }

    public static MyReviewResponseDto ofOrder(
            StoreReview review,
            List<StoreReviewImage> images,
            List<OrderItem> orderItems,
            Function<String, String> imageUrlResolver
    ) {
        return MyReviewResponseDto.builder()
                .storereviewId(review.getStorereviewId())
                .reviewType(review.getReviewType())
                .storeId(review.getStore().getStoreId())
                .storeName(review.getStore().getName())
                .rating(review.getRating())
                .content(review.getContent())
                .images(toImageDtos(images, imageUrlResolver))
                .orderId(review.getOrder().getOrderId())
                .orderItems(orderItems == null ? Collections.emptyList()
                        : orderItems.stream().map(OrderItemSummaryDto::from).toList())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public static MyReviewResponseDto ofReservation(
            StoreReview review,
            List<StoreReviewImage> images
    ) {
        return ofReservation(review, images, Function.identity());
    }

    public static MyReviewResponseDto ofReservation(
            StoreReview review,
            List<StoreReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return MyReviewResponseDto.builder()
                .storereviewId(review.getStorereviewId())
                .reviewType(review.getReviewType())
                .storeId(review.getStore().getStoreId())
                .storeName(review.getStore().getName())
                .rating(review.getRating())
                .content(review.getContent())
                .images(toImageDtos(images, imageUrlResolver))
                .visitReservationId(review.getVisitReservation().getVisitReservationId())
                .visitDate(review.getVisitReservation().getVisitDate())
                .visitTime(review.getVisitReservation().getVisitTime())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private static List<ImageResponseDto> toImageDtos(
            List<StoreReviewImage> images,
            Function<String, String> imageUrlResolver
    ) {
        return images.stream()
                .map(img -> ImageResponseDto.builder()
                        .imageId(img.getStorereviewimageId())
                        .imageUrl(imageUrlResolver.apply(img.getImageUrl()))
                        .displayOrder(img.getDisplayOrder())
                        .isThumbnail(img.isThumbnail())
                        .build())
                .toList();
    }

    @Getter
    @Builder
    @Schema(description = "주문 상품 요약")
    public static class OrderItemSummaryDto {

        @Schema(description = "상품명", example = "스프가 맛있는 우유")
        private String productName;

        @Schema(description = "상품 썸네일 URL")
        private String thumbnailUrl;

        @Schema(description = "수량", example = "2")
        private Integer quantity;

        public static OrderItemSummaryDto from(OrderItem orderItem) {
            return OrderItemSummaryDto.builder()
                    .productName(orderItem.getProductName())
                    .thumbnailUrl(orderItem.getThumbnailUrl())
                    .quantity(orderItem.getQuantity())
                    .build();
        }
    }
}
