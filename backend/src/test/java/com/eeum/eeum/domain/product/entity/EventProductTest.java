package com.eeum.eeum.domain.product.entity;

import com.eeum.eeum.domain.product.enums.EventProductDisplayStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        Store store = Store.createForOwnerSignup(null, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 1L);

        product = Product.create(store, null, "테스트 상품", null, BigDecimal.valueOf(10000), 100, ProductType.SALE);
        ReflectionTestUtils.setField(product, "productId", 1L);
    }

    private EventProduct createOngoing() {
        return EventProduct.create(product, BigDecimal.valueOf(8000), 10,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusHours(1));
    }

    private EventProduct createScheduled() {
        return EventProduct.create(product, BigDecimal.valueOf(8000), 10,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3));
    }

    private EventProduct createExpired() {
        return EventProduct.create(product, BigDecimal.valueOf(8000), 10,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(30));
    }

    // ──────────────────── resolveDisplayStatus ────────────────────

    @Test
    void ACTIVE_상태이고_진행중이면_ONGOING_반환() {
        // given
        EventProduct ep = createOngoing();

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.ONGOING);
    }

    @Test
    void ACTIVE_상태이고_시작_전이면_SCHEDULED_반환() {
        // given
        EventProduct ep = createScheduled();

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.SCHEDULED);
    }

    @Test
    void ACTIVE_상태이고_재고가_소진되면_SOLD_OUT_반환() {
        // given
        EventProduct ep = createOngoing();
        ep.decreaseStock(10); // 전체 재고 소진

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.SOLD_OUT);
    }

    @Test
    void ENDED_상태이면_ENDED_반환() {
        // given
        EventProduct ep = createOngoing();
        ep.end();

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.ENDED);
    }

    // [시나리오 7] DELETED 상태 이벤트는 앱에서 ENDED로 표시
    @Test
    void DELETED_상태이면_ENDED_반환() {
        // given
        EventProduct ep = createOngoing();
        ep.delete();

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.ENDED);
    }

    @Test
    void endAt이_지나면_ENDED_반환() {
        // given
        EventProduct ep = createExpired(); // endAt이 이미 과거

        // when
        EventProductDisplayStatus status = ep.resolveDisplayStatus();

        // then
        assertThat(status).isEqualTo(EventProductDisplayStatus.ENDED);
    }

    // ──────────────────── isOngoing ────────────────────

    @Test
    void ACTIVE_상태이고_현재_시간이_범위_안이면_진행중_true() {
        // given
        EventProduct ep = createOngoing();

        // when & then
        assertThat(ep.isOngoing()).isTrue();
    }

    // [시나리오 2] 이벤트 종료 후 카트에 남은 이벤트 상품 — isOngoing()이 false 반환
    @Test
    void ACTIVE_상태이지만_endAt이_지나면_진행중_false() {
        // given
        EventProduct ep = createExpired();

        // when & then
        assertThat(ep.isOngoing()).isFalse();
    }

    @Test
    void ENDED_상태이면_진행중_false() {
        // given
        EventProduct ep = createOngoing();
        ep.end();

        // when & then
        assertThat(ep.isOngoing()).isFalse();
    }

    @Test
    void ACTIVE_상태이지만_시작_전이면_진행중_false() {
        // given
        EventProduct ep = createScheduled();

        // when & then
        assertThat(ep.isOngoing()).isFalse();
    }

    // ──────────────────── decreaseStock ────────────────────

    @Test
    void 재고_차감_성공() {
        // given
        EventProduct ep = createOngoing();

        // when
        ep.decreaseStock(3);

        // then
        assertThat(ep.getSoldCount()).isEqualTo(3);
        assertThat(ep.getRemainingStock()).isEqualTo(7);
    }

    @Test
    void 재고보다_많이_차감하면_PRODUCT_OUT_OF_STOCK() {
        // given
        EventProduct ep = createOngoing();

        // when & then
        assertThatThrownBy(() -> ep.decreaseStock(11))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    @Test
    void 잔여_재고_0인_상태에서_차감하면_PRODUCT_OUT_OF_STOCK() {
        // given
        EventProduct ep = createOngoing();
        ep.decreaseStock(10); // 전량 소진

        // when & then
        assertThatThrownBy(() -> ep.decreaseStock(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    // ──────────────────── restoreStock ────────────────────

    // [시나리오 6] 이벤트 종료 후 기존 주문 취소 → soldCount 롤백 정상 처리
    @Test
    void 주문_취소_시_재고_복원_성공() {
        // given
        EventProduct ep = createOngoing();
        ep.decreaseStock(5);

        // when
        ep.restoreStock(3);

        // then
        assertThat(ep.getSoldCount()).isEqualTo(2);
        assertThat(ep.getRemainingStock()).isEqualTo(8);
    }

    @Test
    void 전체_수량_복원_시_soldCount가_0이_됨() {
        // given
        EventProduct ep = createOngoing();
        ep.decreaseStock(7);

        // when
        ep.restoreStock(7);

        // then
        assertThat(ep.getSoldCount()).isEqualTo(0);
        assertThat(ep.getRemainingStock()).isEqualTo(10);
    }

    @Test
    void 수량_0_이하로_복원_시_COMMON_INVALID_PARAMETER() {
        // given
        EventProduct ep = createOngoing();

        // when & then
        assertThatThrownBy(() -> ep.restoreStock(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void 음수_수량으로_복원_시_COMMON_INVALID_PARAMETER() {
        // given
        EventProduct ep = createOngoing();

        // when & then
        assertThatThrownBy(() -> ep.restoreStock(-1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_PARAMETER);
    }

    @Test
    void soldCount보다_많은_수량_복원_시_0으로_클램핑() {
        // given
        EventProduct ep = createOngoing();
        ep.decreaseStock(2); // soldCount=2

        // when
        ep.restoreStock(5); // soldCount(2)보다 많이 복원

        // then
        assertThat(ep.getSoldCount()).isEqualTo(0); // Math.max(0, 2-5) = 0
    }

    // ──────────────────── getRemainingStock ────────────────────

    @Test
    void 잔여_재고는_이벤트재고에서_판매수량을_뺀_값() {
        // given
        EventProduct ep = createOngoing(); // eventStock=10, soldCount=0

        ep.decreaseStock(4);

        // when
        int remaining = ep.getRemainingStock();

        // then
        assertThat(remaining).isEqualTo(6);
    }
}
