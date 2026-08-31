package com.eeum.eeum.domain.used.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고 게시글 도메인 규칙 테스트.
 *
 * <p>외부 의존성이 없는 순수 엔티티 테스트라 Mock을 쓰지 않는다.
 */
class UsedProductTest {

    private static final Long SELLER_ID = 1L;

    // ─────────────────── 생성 ───────────────────

    @Test
    void 생성하면_판매중_노출_미삭제_상태로_시작한다() {
        // when
        UsedProduct product = fixedPriceProduct();

        // then
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
        assertThat(product.isHidden()).isFalse();
        assertThat(product.isDeleted()).isFalse();
        assertThat(product.getViewCount()).isZero();
        assertThat(product.getFavoriteCount()).isZero();
    }

    @Test
    void 중고_카테고리가_아니면_생성할_수_없다() {
        // given — 서비스에만 검증을 두면 다른 생성 경로에서 가게 카테고리가 그대로 들어온다
        Category storeCategory = category(CategoryType.STORE);

        // when & then
        assertThatThrownBy(() -> UsedProduct.create(
                seller(), storeCategory, region(), "제목", "본문",
                UsedProductPriceType.FIXED, new BigDecimal("10000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
    }

    // ─────────────────── 가격 정합성 ───────────────────

    @Test
    void 정가는_0보다_커야_한다() {
        assertThatCode(() -> create(UsedProductPriceType.FIXED, new BigDecimal("10000")))
                .doesNotThrowAnyException();

        assertPriceRejected(UsedProductPriceType.FIXED, null);
        assertPriceRejected(UsedProductPriceType.FIXED, BigDecimal.ZERO);
    }

    @Test
    void 나눔은_0원이어야_한다() {
        assertThatCode(() -> create(UsedProductPriceType.FREE, BigDecimal.ZERO))
                .doesNotThrowAnyException();

        assertPriceRejected(UsedProductPriceType.FREE, null);
        assertPriceRejected(UsedProductPriceType.FREE, new BigDecimal("10000"));
    }

    @Test
    void 나눔의_0원은_소수점_표기가_달라도_통과한다() {
        // given — DB 컬럼이 scale=2라 조회 시 0.00으로 돌아온다.
        // equals로 비교했다면 BigDecimal.ZERO와 다른 값으로 판정돼 실패한다.
        assertThatCode(() -> create(UsedProductPriceType.FREE, new BigDecimal("0.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void 가격제안은_금액을_담을_수_없다() {
        assertThatCode(() -> create(UsedProductPriceType.NEGOTIABLE, null))
                .doesNotThrowAnyException();

        // 허용만 하고 강제하지 않으면 "가격제안인데 1만원"인 행이 남는다
        assertPriceRejected(UsedProductPriceType.NEGOTIABLE, new BigDecimal("10000"));
        assertPriceRejected(UsedProductPriceType.NEGOTIABLE, BigDecimal.ZERO);
    }

    // ─────────────────── 거래 상태 전이 ───────────────────

    @Test
    void 판매중이면_예약할_수_있다() {
        UsedProduct product = fixedPriceProduct();

        product.reserve();

        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.RESERVED);
    }

    @Test
    void 예약중인_글은_다시_예약할_수_없다() {
        UsedProduct product = fixedPriceProduct();
        product.reserve();

        assertThatThrownBy(product::reserve)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ALREADY_RESERVED);
    }

    @Test
    void 예약을_취소하면_판매중으로_돌아간다() {
        UsedProduct product = fixedPriceProduct();
        product.reserve();

        product.cancelReservation();

        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SELLING);
    }

    @Test
    void 예약중이_아닌_글은_예약을_취소할_수_없다() {
        UsedProduct product = fixedPriceProduct();

        assertThatThrownBy(product::cancelReservation)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_ON_SALE);
    }

    @Test
    void 예약을_거치지_않고도_바로_거래를_완료할_수_있다() {
        // given — 현장에서 바로 성사되는 직거래에 예약을 강제하지 않는다
        UsedProduct product = fixedPriceProduct();

        // when
        product.markSold();

        // then
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
    }

    @Test
    void 예약중인_글도_거래를_완료할_수_있다() {
        UsedProduct product = fixedPriceProduct();
        product.reserve();

        product.markSold();

        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
    }

    @Test
    void 판매완료된_글은_어떤_상태로도_되돌릴_수_없다() {
        UsedProduct sold = fixedPriceProduct();
        sold.markSold();

        assertSoldRejected(sold::reserve);
        assertSoldRejected(sold::cancelReservation);
        assertSoldRejected(sold::markSold);
    }

    // ─────────────────── 노출 / 삭제 ───────────────────

    @Test
    void 관리자_숨김은_거래_상태를_건드리지_않는다() {
        // given — 숨김과 거래 상태는 독립된 축이다
        UsedProduct product = fixedPriceProduct();
        product.reserve();

        // when
        product.hide();

        // then
        assertThat(product.isHidden()).isTrue();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.RESERVED);

        product.show();
        assertThat(product.isHidden()).isFalse();
    }

    @Test
    void 삭제하면_deletedAt이_남고_상태는_유지된다() {
        // given — 판매완료 글의 후기·채팅·신고 이력이 가리킬 대상을 남겨야 한다
        UsedProduct product = fixedPriceProduct();
        product.markSold();

        // when
        product.softDelete();

        // then
        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.SOLD);
    }

    @Test
    void 작성자_본인만_소유자로_판정된다() {
        UsedProduct product = fixedPriceProduct();

        assertThat(product.isOwnedBy(SELLER_ID)).isTrue();
        assertThat(product.isOwnedBy(999L)).isFalse();
    }

    // ─────────────────── 헬퍼 ───────────────────

    private UsedProduct fixedPriceProduct() {
        return create(UsedProductPriceType.FIXED, new BigDecimal("10000"));
    }

    private UsedProduct create(UsedProductPriceType priceType, BigDecimal price) {
        return UsedProduct.create(
                seller(), category(CategoryType.USED), region(),
                "자전거 팝니다", "거의 새것입니다", priceType, price);
    }

    private void assertPriceRejected(UsedProductPriceType priceType, BigDecimal price) {
        assertThatThrownBy(() -> create(priceType, price))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_PRICE);
    }

    private void assertSoldRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ALREADY_SOLD);
    }

    private Account seller() {
        Account account = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", SELLER_ID);
        return account;
    }

    private Category category(CategoryType type) {
        return Category.createRoot(type, "디지털기기", 1);
    }

    private Region region() {
        return Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3);
    }
}
