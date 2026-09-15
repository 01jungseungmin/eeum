package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 완료 주문의 종료 상태 전이를 고정한다.
 *
 * 서비스 검증만으로는 다른 진입점이 완료 주문을 취소할 수 있었다.
 * 엔티티도 완료 상태의 취소를 거절해 상태 전이 우회를 막는다.
 */
class OrderTest {

    @Test
    void 거래완료_주문은_취소할_수_없다() {
        Order order = Order.create(
                org.mockito.Mockito.mock(Account.class),
                Store.createForOwnerSignup(org.mockito.Mockito.mock(Account.class), "테스트 상점", "서울", "010-0000-0000"),
                BigDecimal.TEN, "order-1", OrderType.SALE, null, null);
        order.complete();

        assertThatThrownBy(() -> order.cancel("고객 취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
    }
}
