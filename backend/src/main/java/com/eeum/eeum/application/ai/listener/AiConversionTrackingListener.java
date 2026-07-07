package com.eeum.eeum.application.ai.listener;

import com.eeum.eeum.application.ai.service.AiConversionService;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.reservation.event.ReservationCreatedEvent;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

// 주문 결제/예약 생성 이벤트를 구독해 AI 메시지 전환을 기록 — 실패해도 본 흐름에 영향 없음
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConversionTrackingListener {

    private final AiConversionService aiConversionService;
    private final OrderRepository orderRepository;
    private final VisitReservationRepository visitReservationRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaid(OrderPaidEvent event) {
        try {
            orderRepository.findById(event.orderId()).ifPresent(order ->
                    aiConversionService.recordConversion(
                            event.customerAccountId(),
                            order.getStore().getStoreId(),
                            AiConversionType.ORDER,
                            event.orderId(),
                            null,
                            LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[AI-CONVERSION] 주문 전환 기록 실패: orderId={}", event.orderId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCreated(ReservationCreatedEvent event) {
        try {
            visitReservationRepository.findById(event.reservationId()).ifPresent(reservation ->
                    aiConversionService.recordConversion(
                            reservation.getAccount().getAccountId(),
                            reservation.getStore().getStoreId(),
                            AiConversionType.RESERVATION,
                            null,
                            event.reservationId(),
                            LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[AI-CONVERSION] 예약 전환 기록 실패: reservationId={}", event.reservationId(), e);
        }
    }
}
