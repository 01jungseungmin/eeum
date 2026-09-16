package com.eeum.eeum.application.ai.listener;

import com.eeum.eeum.application.ai.service.AiConversionService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.reservation.entity.VisitReservation;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversionTrackingListenerTest {

    @InjectMocks
    private AiConversionTrackingListener listener;

    @Mock private AiConversionService aiConversionService;
    @Mock private OrderRepository orderRepository;
    @Mock private VisitReservationRepository visitReservationRepository;

    private static final Long CUSTOMER_ID = 10L;
    private static final Long STORE_ID = 5L;
    private static final Long ORDER_ID = 100L;
    private static final Long RESERVATION_ID = 200L;

    // ──────────────────── Helpers ────────────────────

    private OrderPaidEvent orderPaidEvent() {
        return new OrderPaidEvent(CUSTOMER_ID, "가게", "ORD-1", new BigDecimal("10000"), ORDER_ID);
    }

    private ReservationCreatedEvent reservationCreatedEvent() {
        return new ReservationCreatedEvent(
                999L, "고객", "가게", LocalDate.now(), LocalTime.NOON, RESERVATION_ID);
    }

    private Store stubStore() {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
    }

    // ──────────────────── 주문 결제 이벤트 ────────────────────

    @Test
    void 주문_결제_이벤트_수신_시_주문_고객과_가게_기준으로_전환이_기록된다() {
        // given
        Store store = stubStore();
        Order order = mock(Order.class);
        when(order.getStore()).thenReturn(store);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // when
        listener.handleOrderPaid(orderPaidEvent());

        // then — accountId/storeId/orderId 인자 순서까지 eq로 검증
        verify(aiConversionService).recordConversion(
                eq(CUSTOMER_ID), eq(STORE_ID), eq(AiConversionType.ORDER),
                eq(ORDER_ID), isNull(), any(LocalDateTime.class));
    }

    @Test
    void 주문이_존재하지_않으면_전환을_기록하지_않는다() {
        // given
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // when
        listener.handleOrderPaid(orderPaidEvent());

        // then
        verifyNoInteractions(aiConversionService);
    }

    @Test
    void 주문_전환_기록이_실패해도_예외가_전파되지_않는다() {
        // given
        Store store = stubStore();
        Order order = mock(Order.class);
        when(order.getStore()).thenReturn(store);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("DB 오류")).when(aiConversionService)
                .recordConversion(anyLong(), anyLong(), any(), any(), any(), any());

        // when & then
        assertThatCode(() -> listener.handleOrderPaid(orderPaidEvent()))
                .doesNotThrowAnyException();
    }

    // ──────────────────── 예약 생성 이벤트 ────────────────────

    @Test
    void 예약_생성_이벤트_수신_시_예약_고객과_가게_기준으로_전환이_기록된다() {
        // given
        Store store = stubStore();
        VisitReservation reservation = mock(VisitReservation.class);
        Account customer = mock(Account.class);
        when(customer.getAccountId()).thenReturn(CUSTOMER_ID);
        when(reservation.getAccount()).thenReturn(customer);
        when(reservation.getStore()).thenReturn(store);
        when(visitReservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        // when
        listener.handleReservationCreated(reservationCreatedEvent());

        // then
        verify(aiConversionService).recordConversion(
                eq(CUSTOMER_ID), eq(STORE_ID), eq(AiConversionType.RESERVATION),
                isNull(), eq(RESERVATION_ID), any(LocalDateTime.class));
    }

    @Test
    void 예약이_존재하지_않으면_전환을_기록하지_않는다() {
        // given
        when(visitReservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        // when
        listener.handleReservationCreated(reservationCreatedEvent());

        // then
        verifyNoInteractions(aiConversionService);
    }

    @Test
    void 예약_전환_기록이_실패해도_예외가_전파되지_않는다() {
        // given
        when(visitReservationRepository.findById(RESERVATION_ID))
                .thenThrow(new RuntimeException("DB 오류"));

        // when & then
        assertThatCode(() -> listener.handleReservationCreated(reservationCreatedEvent()))
                .doesNotThrowAnyException();
        verifyNoInteractions(aiConversionService);
    }
}
