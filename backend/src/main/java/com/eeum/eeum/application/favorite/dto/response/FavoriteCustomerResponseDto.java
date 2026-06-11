package com.eeum.eeum.application.favorite.dto.response;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.order.repository.CustomerOrderStatProjection;
import com.eeum.eeum.domain.store.repository.CustomerReviewStatProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "사장용 찜 고객 응답")
public class FavoriteCustomerResponseDto {

    @Schema(description = "찜 ID", example = "10")
    private Long favoriteId;

    @Schema(description = "고객명 (마스킹 처리)", example = "이*민")
    private String maskedName;

    @Schema(description = "닉네임", example = "맛집탐험가")
    private String nickname;

    @Schema(description = "프로필 이미지 URL")
    private String profileImageUrl;

    @Schema(description = "찜 등록 일시")
    private LocalDateTime favoritedAt;

    @Schema(description = "완료된 주문 수", example = "12")
    private Long orderCount;

    @Schema(description = "총 구매액 (완료 주문 기준)", example = "156000.00")
    private BigDecimal totalAmount;

    @Schema(description = "최근 주문 일시 (완료 주문 기준, 주문 없으면 null)")
    private LocalDateTime lastOrderedAt;

    @Schema(description = "이 상점에 남긴 평균 평점 (리뷰 없으면 null)", example = "4.5")
    private Double avgRating;

    public static FavoriteCustomerResponseDto of(
            Favorite favorite,
            Account account,
            CustomerOrderStatProjection orderStat,
            CustomerReviewStatProjection reviewStat
    ) {
        return FavoriteCustomerResponseDto.builder()
                .favoriteId(favorite.getFavoriteId())
                .maskedName(maskName(account.getName()))
                .nickname(account.getNickname())
                .profileImageUrl(account.getProfileImageUrl())
                .favoritedAt(favorite.getCreatedAt())
                .orderCount(orderStat != null ? orderStat.getOrderCount() : 0L)
                .totalAmount(orderStat != null ? orderStat.getTotalAmount() : BigDecimal.ZERO)
                .lastOrderedAt(orderStat != null ? orderStat.getLastOrderedAt() : null)
                .avgRating(reviewStat != null ? reviewStat.getAvgRating() : null)
                .build();
    }

    // 이름 마스킹 처리.

    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
