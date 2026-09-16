package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.order.service.PaymentCancellationService;
import com.eeum.eeum.application.settlement.service.OwnerRevenueService;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreOrderCancellationRoutingTest {
    @InjectMocks private StoreOrderService storeOrderService;
    @Mock private FileStorageService fileStorageService;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RedisLockService redisLockService;
    @Mock private PaymentCancellationService paymentCancellationService;
    @Mock private OwnerRevenueService ownerRevenueService;
    @Mock private OwnerOrderCancellationAuthorizer cancellationAuthorizer;

    @Test
    void 사장_주문거절의_결제완료건은_공통_취소_서비스로_위임한다() {
        // given
        when(cancellationAuthorizer.authorizeRejection(10L, 20L)).thenReturn(PaymentStatus.PAID);

        // when
        storeOrderService.rejectOrder(10L, 20L, "재고 부족");

        // then
        verify(paymentCancellationService).cancel(
                eq(20L), eq(PaymentCancellationTrigger.OWNER_ORDER_REJECT), eq("재고 부족"));
    }

    @Test
    void 사장_환불승인은_공통_취소_서비스로_위임한다() {
        // given
        when(cancellationAuthorizer.authorizeRefundApproval(10L, 20L)).thenReturn("고객 요청");

        // when
        storeOrderService.approveRefund(10L, 20L);

        // then
        verify(paymentCancellationService).cancel(
                eq(20L), eq(PaymentCancellationTrigger.OWNER_REFUND_APPROVAL), eq("고객 요청"));
    }
}
