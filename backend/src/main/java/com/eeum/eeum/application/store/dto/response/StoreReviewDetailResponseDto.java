package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.application.order.dto.response.OrderItemResponseDto;
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
@Schema(description = "상점 리뷰 상세 응답")
public class StoreReviewDetailResponseDto {

    @Schema(description = "리뷰 ID", example = "1")
    private Long storereviewId;

    @Schema(description = "리뷰 타입", example = "ORDER")
    private StoreReviewType reviewType;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상점명", example = "승민반찬가게")
    private String storeName;

    @Schema(description = "주문 ID (주문 리뷰인 경우)", example = "42")
    private Long orderId;

    @Schema(description = "주문번호 (주문 리뷰인 경우)")
    private String orderNumber;

    @Schema(description = "주문일시 (주문 리뷰인 경우)")
    private LocalDateTime orderCreatedAt;

    @Schema(description = "주문 상품 목록 (주문 리뷰인 경우)")
    private List<OrderItemResponseDto> orderItems;

    @Schema(description = "방문 예약 ID (예약 리뷰인 경우)")
    private Long visitReservationId;

    @Schema(description = "방문 예약일 (예약 리뷰인 경우)")
    private LocalDate visitDate;

    @Schema(description = "방문 예약시간 (예약 리뷰인 경우)")
    private LocalTime visitTime;

    @Schema(description = "작성자 account ID", example = "10")
    private Long accountId;

    @Schema(description = "작성자 닉네임", example = "동네주민A")
    private String nickname;

    @Schema(description = "별점 (1~5)", example = "5")
    private int rating;

    @Schema(description = "리뷰 내용", example = "음식이 정말 맛있었어요!")
    private String content;

    @Schema(description = "리뷰 이미지 목록")
    private List<ImageResponseDto> images;

    @Schema(description = "사장 답글 (없으면 null)")
    private StoreReviewReplyResponseDto reply;

    @Schema(description = "리뷰 작성일시")
    private LocalDateTime createdAt;

    @Schema(description = "리뷰 수정일시")
    private LocalDateTime modifiedAt;

    // ===================== 정적 변환 메서드 =====================

    public static StoreReviewDetailResponseDto of(
            StoreReview review,
            List<StoreReviewImage> images,
            StoreReviewReplyResponseDto reply,
            List<OrderItem> orderItems
    ) {
        return of(review, images, reply, orderItems, Function.identity());
    }

    public static StoreReviewDetailResponseDto of(
            StoreReview review,
            List<StoreReviewImage> images,
            StoreReviewReplyResponseDto reply,
            List<OrderItem> orderItems,
            Function<String, String> imageUrlResolver
    ) {
        List<ImageResponseDto> imageDtos = images.stream()
                .map(img -> ImageResponseDto.builder()
                        .imageId(img.getStorereviewimageId())
                        .imageUrl(imageUrlResolver.apply(img.getImageUrl()))
                        .displayOrder(img.getDisplayOrder())
                        .isThumbnail(img.isThumbnail())
                        .build())
                .toList();

        StoreReviewDetailResponseDto.StoreReviewDetailResponseDtoBuilder builder =
                StoreReviewDetailResponseDto.builder()
                        .storereviewId(review.getStorereviewId())
                        .reviewType(review.getReviewType())
                        .storeId(review.getStore().getStoreId())
                        .storeName(review.getStore().getName())
                        .accountId(review.getAccount().getAccountId())
                        .nickname(review.getAccount().getNickname())
                        .rating(review.getRating())
                        .content(review.getContent())
                        .images(imageDtos)
                        .reply(reply)
                        .createdAt(review.getCreatedAt())
                        .modifiedAt(review.getModifiedAt());

        if (review.getReviewType() == StoreReviewType.ORDER && review.getOrder() != null) {
            builder.orderId(review.getOrder().getOrderId())
                    .orderNumber(review.getOrder().getOrderNumber())
                    .orderCreatedAt(review.getOrder().getCreatedAt())
                    .orderItems(orderItems == null ? Collections.emptyList()
                            : orderItems.stream().map(OrderItemResponseDto::from).toList());
        } else if (review.getReviewType() == StoreReviewType.RESERVATION && review.getVisitReservation() != null) {
            builder.visitReservationId(review.getVisitReservation().getVisitReservationId())
                    .visitDate(review.getVisitReservation().getVisitDate())
                    .visitTime(review.getVisitReservation().getVisitTime());
        }

        return builder.build();
    }
}
