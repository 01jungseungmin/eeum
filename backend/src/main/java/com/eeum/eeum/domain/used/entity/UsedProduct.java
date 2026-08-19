package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
// 변경된 컬럼만 UPDATE한다. 전체 컬럼을 쓰면 favoriteCount·viewCount처럼 별도 원자 UPDATE로
// 증감되는 값이, 이전에 읽어둔 엔티티의 오래된 값으로 덮어써져 조용히 유실된다.
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "used_product",
        indexes = {
                // 동네 목록 기본 조회: 지역 + 상태 + 최신순
                @Index(name = "idx_used_product_region_status", columnList = "region_id, status, created_at"),
                // 내가 쓴 글 목록
                @Index(name = "idx_used_product_seller", columnList = "account_id")
        }
)
public class UsedProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "used_product_id")
    private Long usedProductId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // 거래 희망 동네. 작성 시점의 값으로 고정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20)
    private UsedProductPriceType priceType;

    // NEGOTIABLE이면 null {@link #validatePrice}가 유형별 규칙을 강제
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UsedProductStatus status;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    // 찜 수. 증감은 FavoriteService가 DB 원자 UPDATE로 처리
    @Column(name = "favorite_count", nullable = false)
    private int favoriteCount;

    // ===================== 정적 팩토리 메서드 =====================

    public static UsedProduct create(
            Account seller,
            Category category,
            Region region,
            String title,
            String content,
            UsedProductPriceType priceType,
            BigDecimal price
    ) {
        validateCategory(category);
        validatePrice(priceType, price);

        UsedProduct product = new UsedProduct();
        product.seller = seller;
        product.category = category;
        product.region = region;
        product.title = title;
        product.content = content;
        product.priceType = priceType;
        product.price = price;
        product.status = UsedProductStatus.SELLING;
        product.hidden = false;
        product.viewCount = 0;
        product.favoriteCount = 0;
        return product;
    }

    // 게시글 내용 수정
    public void updateInfo(
            Category category,
            String title,
            String content,
            UsedProductPriceType priceType,
            BigDecimal price
    ) {
        validateCategory(category);
        validatePrice(priceType, price);

        this.category = category;
        this.title = title;
        this.content = content;
        this.priceType = priceType;
        this.price = price;
    }

    // ===================== 거래 상태 =====================

    // 판매중인 글만 예약 가능
    public void reserve() {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        if (this.status == UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_RESERVED);
        }
        this.status = UsedProductStatus.RESERVED;
    }

    // 예약을 취소해 다시 판매중으로 롤백
    public void cancelReservation() {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        if (this.status != UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_NOT_ON_SALE);
        }
        this.status = UsedProductStatus.SELLING;
    }

    // 거래 완료 처리
    public void markSold() {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        this.status = UsedProductStatus.SOLD;
    }

    // ===================== 노출 / 삭제 =====================

    //관리자 숨김. 거래 상태는 변화 x
    public void hide() {
        this.hidden = true;
    }

    public void show() {
        this.hidden = false;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.seller.getAccountId().equals(accountId);
    }

    // ===================== 내부 검증 =====================

    private static void validateCategory(Category category) {
        // 서비스에만 두면 다른 생성 경로가 생겼을 때 STORE 카테고리가 그대로 들어온다.
        if (category == null || category.getType() != CategoryType.USED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
        }
    }

    //거래 유형과 가격의 정합성 강제 DB에서 돌아온 {@code 0.00}이 {@code BigDecimal.ZERO}와 다른 값으로 판정
    private static void validatePrice(UsedProductPriceType priceType, BigDecimal price) {
        boolean valid = switch (priceType) {
            case FIXED -> price != null && price.compareTo(BigDecimal.ZERO) > 0;
            case FREE -> price != null && price.compareTo(BigDecimal.ZERO) == 0;
            case NEGOTIABLE -> price == null;
        };

        if (!valid) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_PRICE);
        }
    }
}
