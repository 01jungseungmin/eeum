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
                // 공개 목록 기본 조회: region + (삭제·숨김 제외) + 최신순.
                // 상태 필터는 선택값이라 위 인덱스는 status가 없으면 created_at까지 닿지 못한다.
                // 모든 공개 조회가 반드시 거는 조건만 순서대로 담았다.
                // TODO: 운영 데이터로 EXPLAIN 확인 후 컬럼 순서 재검토
                @Index(name = "idx_used_product_public_list",
                        columnList = "region_id, deleted_at, is_hidden, created_at"),
                // 관리자 전체 목록 최신순 조회
                @Index(name = "idx_used_product_admin_created", columnList = "created_at"),
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

    // ===== 공개용 "대략" 거래 장소 =====
    // 목록·상세로 누구에게나 노출된다. 정확한 주소는 담지 않는다 — 실제 약속 장소는
    // 채팅에서 당사자끼리 정한다. 여기 들어오는 값은 "○○동 주민센터 앞" 같은 공개 장소다.
    //
    // 셋 다 null이어도 된다. 그 경우 region이 "동네만 지정"을 담당한다.

    // 표시용 장소명
    @Column(name = "trade_location_name", length = 255)
    private String tradeLocationName;

    @Column(name = "trade_latitude")
    private Double tradeLatitude;

    @Column(name = "trade_longitude")
    private Double tradeLongitude;

    // 카카오 장소 ID. 지도에서 직접 찍은 핀은 값이 없다.
    @Column(name = "trade_place_id", length = 50)
    private String tradePlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UsedProductStatus status;

    /**
     * 거래 상대(구매자). 예약·판매완료 시 판매자가 지정한다.
     *
     * <p><b>nullable이다.</b> 앱 밖에서 성사된 거래를 판매완료로 정리하거나 상대 없이
     * "예약중"만 표시하는 경우가 있어, 구매자 지정을 강제하면 그런 글을 SOLD로 만들 수 없다.
     * 대신 후기는 이 값이 있는 거래에서만 쓸 수 있다.
     *
     * <p>판매자와 같은 계정은 지정할 수 없다 — 자기 거래에 후기를 남기는 경로가 생긴다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_account_id")
    private Account buyer;

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

    /**
     * 거래 장소 없이 등록한다. 이 경우 region이 "동네만 지정"을 담당한다 —
     * 장소 지정은 선택이므로 아래 전체 인자 버전과 나란히 정식 경로다.
     */
    public static UsedProduct create(
            Account seller,
            Category category,
            Region region,
            String title,
            String content,
            UsedProductPriceType priceType,
            BigDecimal price
    ) {
        return create(seller, category, region, title, content, priceType, price,
                null, null, null, null);
    }

    public static UsedProduct create(
            Account seller,
            Category category,
            Region region,
            String title,
            String content,
            UsedProductPriceType priceType,
            BigDecimal price,
            String tradeLocationName,
            Double tradeLatitude,
            Double tradeLongitude,
            String tradePlaceId
    ) {
        validateCategory(category);
        validatePrice(priceType, price);

        // 빈 문자열은 "없음"과 같은 뜻이지만 DB에는 NOT NULL 값으로 남는다. 정규화하지 않으면
        // 검증은 통과한 뒤 flush 시점에 CHECK 제약이 터져 400 대신 500이 나간다.
        tradeLocationName = blankToNull(tradeLocationName);
        tradePlaceId = blankToNull(tradePlaceId);
        validateTradeLocation(tradeLocationName, tradeLatitude, tradeLongitude, tradePlaceId);

        UsedProduct product = new UsedProduct();
        product.seller = seller;
        product.category = category;
        product.region = region;
        product.title = title;
        product.content = content;
        product.priceType = priceType;
        product.price = price;
        product.tradeLocationName = tradeLocationName;
        product.tradeLatitude = tradeLatitude;
        product.tradeLongitude = tradeLongitude;
        product.tradePlaceId = tradePlaceId;
        product.status = UsedProductStatus.SELLING;
        product.hidden = false;
        product.viewCount = 0;
        product.favoriteCount = 0;
        return product;
    }

    // 게시글 내용 수정. 거래 장소도 함께 바꾼다 — 셋을 비워 보내면 장소 지정이 해제된다.
    public void updateInfo(
            Category category,
            String title,
            String content,
            UsedProductPriceType priceType,
            BigDecimal price,
            String tradeLocationName,
            Double tradeLatitude,
            Double tradeLongitude,
            String tradePlaceId
    ) {
        validateCategory(category);
        validatePrice(priceType, price);

        // 생성 경로와 같은 이유로 정규화한다 — 수정으로 빈 문자열이 들어와도 결과는 같아야 한다.
        tradeLocationName = blankToNull(tradeLocationName);
        tradePlaceId = blankToNull(tradePlaceId);
        validateTradeLocation(tradeLocationName, tradeLatitude, tradeLongitude, tradePlaceId);

        this.category = category;
        this.title = title;
        this.content = content;
        this.priceType = priceType;
        this.price = price;
        this.tradeLocationName = tradeLocationName;
        this.tradeLatitude = tradeLatitude;
        this.tradeLongitude = tradeLongitude;
        this.tradePlaceId = tradePlaceId;
    }

    // ===================== 거래 상태 =====================

    // 판매중인 글만 예약 가능. buyer는 생략할 수 있다(상대 없이 "예약중"만 표시하는 경우).
    public void reserve(Account buyer) {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        if (this.status == UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_RESERVED);
        }
        assertNotSeller(buyer);
        this.status = UsedProductStatus.RESERVED;
        this.buyer = buyer;
    }

    // 예약을 취소해 다시 판매중으로 롤백. 지정했던 구매자도 함께 비운다 —
    // 남겨두면 취소된 거래의 상대가 후기 자격을 갖는다.
    public void cancelReservation() {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        if (this.status != UsedProductStatus.RESERVED) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_NOT_ON_SALE);
        }
        this.status = UsedProductStatus.SELLING;
        this.buyer = null;
    }

    /**
     * 거래 완료 처리. 후기 자격의 근거는 이 시점에 확정된 구매자다.
     *
     * <p>예약을 거치지 않은 즉시 거래(SELLING → SOLD)도 허용하므로 여기서도 구매자를 받는다.
     * 예약 때 지정해 둔 구매자가 있고 이번에 생략하면 그 값을 유지한다 —
     * 예약 상대와 그대로 거래한 흐름에서 구매자가 사라지면 후기를 쓸 수 없다.
     */
    public void markSold(Account buyer) {
        if (this.status == UsedProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
        }
        assertNotSeller(buyer);
        this.status = UsedProductStatus.SOLD;
        if (buyer != null) {
            this.buyer = buyer;
        }
    }

    // 구매자로 지정된 계정인지 — 후기 작성 자격 판정에 쓴다.
    public boolean isPurchasedBy(Long accountId) {
        return this.status == UsedProductStatus.SOLD
                && this.buyer != null
                && this.buyer.getAccountId().equals(accountId);
    }

    private void assertNotSeller(Account buyer) {
        if (buyer != null && buyer.getAccountId().equals(this.seller.getAccountId())) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_BUYER);
        }
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

    // 공개 노출 가능 여부 — 조건은 UsedProductVisibilityPredicate와 같아야 한다.
    // 판매자가 탈퇴하면 글도 함께 내린다. 탈퇴자에게 거래 문의가 계속 가는 것을 막는다.
    public boolean isPubliclyVisible() {
        return !isDeleted() && !hidden && seller.isActive();
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

    // 빈 문자열·공백은 "지정하지 않음"으로 본다. DB CHECK는 NULL만 "없음"으로 취급하므로
    // 여기서 맞춰두지 않으면 Java 검증과 DB 제약의 판정이 갈린다.
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 거래 장소의 정합성 강제.
     *
     * <p>장소명·위도·경도는 <b>셋 다 있거나 셋 다 없다.</b> 부분 입력은 지도에 그릴 수도,
     * 이름만 보여줄 수도 없는 반쪽 데이터가 된다.
     *
     * <p>{@code placeId}는 카카오 검색을 거치지 않고 지도에서 직접 찍은 핀이면 없으므로
     * 있어도 되고 없어도 된다. 다만 장소 자체가 없는데 장소 ID만 남는 것은 막는다.
     *
     * <p>좌표 범위는 서버가 확인한다 — 프론트에서 검색 결과만 고르게 막아도 API를
     * 직접 호출하면 임의 좌표가 들어온다.
     */
    private static void validateTradeLocation(
            String name,
            Double latitude,
            Double longitude,
            String placeId
    ) {
        boolean hasName = name != null && !name.isBlank();
        boolean allPresent = hasName && latitude != null && longitude != null;
        boolean allAbsent = !hasName && latitude == null && longitude == null;

        if (!allPresent && !allAbsent) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_TRADE_LOCATION);
        }
        if (allAbsent) {
            if (placeId != null && !placeId.isBlank()) {
                throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_TRADE_LOCATION);
            }
            return;
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.USED_PRODUCT_INVALID_TRADE_LOCATION);
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
