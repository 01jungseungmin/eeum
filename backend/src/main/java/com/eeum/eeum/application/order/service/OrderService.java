package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.dto.request.RefundRequestDto;
import com.eeum.eeum.application.order.dto.response.OrderItemResponseDto;
import com.eeum.eeum.application.order.dto.response.OrderPaymentReadyResponseDto;
import com.eeum.eeum.application.order.dto.response.OrderResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.*;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.*;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.event.ProductStockWarningEvent;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final FileStorageService fileStorageService;
    private final AccountRepository accountRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final ProductImageRepository productImageRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final RedisLockService redisLockService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Duration ORDER_LOCK_LEASE_TIME = Duration.ofSeconds(10);

    // 재고가 이 값 이하로 떨어지면 사장에게 STOCK_WARNING 알림
    private static final int LOW_STOCK_THRESHOLD = 5;

    @Transactional
    public OrderPaymentReadyResponseDto createOrder(
            Long accountId,
            OrderCreateRequestDto request
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Cart cart = cartRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findByCart_CartId(cart.getCartId());

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        OrderType orderType = resolveOrderType(cartItems);

        validatePickupPolicy(orderType, request.getPickupScheduledAt());

        validateAndDecreaseStock(cartItems);

        BigDecimal totalPrice = cartItems.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String orderNumber = generateOrderNumber();

        Order order = Order.create(
                account,
                cart.getStore(),
                totalPrice,
                orderNumber,
                orderType,
                request.getPickupScheduledAt(),
                request.getRequestMessage()
        );

        orderRepository.save(order);

        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> OrderItem.createFromCartItem(
                        order,
                        item,
                        resolveThumbnailUrl(item)
                ))
                .toList();

        orderItemRepository.saveAll(orderItems);

        String paymentId = createPaymentId(orderNumber, request.getPaymentMethod());

        PaymentStatus paymentStatus = request.getPaymentMethod() == PaymentMethod.CASH_ON_SITE
                ? PaymentStatus.NOT_PAID
                : PaymentStatus.PENDING;

        Payment payment = Payment.create(
                order,
                account,
                paymentId,
                orderNumber,
                totalPrice,
                request.getPaymentMethod(),
                paymentStatus
        );

        paymentRepository.save(payment);

        cartItemRepository.deleteByCart_CartId(cart.getCartId());
        cart.clear();

        log.info("주문 생성: accountId={}, orderNumber={}, orderType={}, paymentMethod={}",
                accountId, orderNumber, orderType, request.getPaymentMethod());

        // 현장결제는 별도 결제 단계가 없으므로 주문 생성 시점에 사장에게 NEW_ORDER 알림.
        // 온라인 결제는 결제 완료(PaymentService) 시점에 발행한다.
        if (request.getPaymentMethod() == PaymentMethod.CASH_ON_SITE) {
            eventPublisher.publishEvent(new OrderPlacedEvent(
                    order.getStore().getAccount().getAccountId(),
                    account.getName(),
                    order.getStore().getName(),
                    order.getOrderNumber(),
                    order.getOrderId()
            ));
        }

        return OrderPaymentReadyResponseDto.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .paymentId(paymentId)
                .orderName(createOrderName(orderItems))
                .storeId(order.getStore().getStoreId())
                .storeName(order.getStore().getName())
                .paymentMethod(request.getPaymentMethod())
                .totalPrice(totalPrice)
                .pickupScheduledAt(order.getPickupScheduledAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getMyOrders(Long accountId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable);

        List<Long> orderIds = orders.getContent().stream().map(Order::getOrderId).toList();
        Set<Long> reviewedOrderIds = orderIds.isEmpty()
                ? Set.of()
                : storeReviewRepository.findOrderIdsWithReview(orderIds);

        return orders.map(order -> {
            List<OrderItem> items = orderItemRepository
                    .findByOrder_OrderId(order.getOrderId());

            Payment payment = paymentRepository
                    .findByOrder_OrderId(order.getOrderId())
                    .orElse(null);

            boolean hasReview = reviewedOrderIds.contains(order.getOrderId());

            return toOrderDto(order, items, payment, hasReview);
        });
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderDetail(Long accountId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);

        Payment payment = paymentRepository
                .findByOrder_OrderId(orderId)
                .orElse(null);

        boolean hasReview = storeReviewRepository.existsByOrder_OrderId(orderId);

        return toOrderDto(order, items, payment, hasReview);
    }

    @Transactional
    public void cancelOrder(Long accountId, Long orderId) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                ORDER_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> cancelOrderWithLock(accountId, orderId)
        );
    }

    @Transactional
    public void expirePendingOrder(Long orderId) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                ORDER_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> expirePendingOrderWithLock(orderId)
        );
    }

    // 사장 주문 거절 시 재고 복구용 — StoreOrderService에서 호출
    @Transactional
    public void restoreStockForOrder(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(orderId);
        restoreStock(orderItems);
    }

    // 고객 PAID 결제 취소(환불) 후 주문 취소 + 재고 복원 — PortOne 환불은 PaymentService가 먼저 수행한다.
    // 주문 락 안에서 이미 CANCELLED/EXPIRED면 스킵해 재고 이중 복원을 방지한다.
    @Transactional
    public void cancelPaidOrder(Long orderId) {
        redisLockService.executeWithLock(
                LockKeys.order(orderId),
                ORDER_LOCK_LEASE_TIME,
                ErrorCode.LOCK_ORDER_FAILED,
                () -> {
                    Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
                    if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
                        return;
                    }
                    restoreStock(orderItemRepository.findByOrder_OrderId(orderId));
                    order.cancel("고객 결제 취소");
                }
        );
    }

    @Transactional
    public void requestOrderRefund(Long accountId, Long orderId, RefundRequestDto request) {
        Order order = orderRepository
                .findByOrderIdAndAccount_AccountId(orderId, accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus()!=OrderStatus.PAID
                &&order.getStatus()!=OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_NOT_ALLOWED);
        }

        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.requestRefund(request.getReason());
    }

    // ===================== 내부 유틸 =====================

    private OrderType resolveOrderType(List<CartItem> cartItems) {
        ProductType firstType = resolveProductType(cartItems.get(0));

        if (firstType == ProductType.MENU) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        }

        for (CartItem item : cartItems) {
            ProductType currentType = resolveProductType(item);

            if (currentType == ProductType.MENU) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
            }

            if (currentType != firstType) {
                throw new BusinessException(ErrorCode.ORDER_TYPE_MISMATCH);
            }
        }

        if (firstType == ProductType.PREORDER) {
            return OrderType.PREORDER;
        }

        return OrderType.SALE;
    }

    private ProductType resolveProductType(CartItem item) {
        if (item.getProduct() != null) {
            return item.getProduct().getProductType();
        }

        return ProductType.SALE;
    }

    private void validatePickupPolicy(OrderType orderType, LocalDateTime pickupScheduledAt) {
        if (orderType == OrderType.PREORDER && pickupScheduledAt == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validateAndDecreaseStock(List<CartItem> cartItems) {
        for (CartItem item : cartItems) {
            if (item.getProduct() != null) {
                Product product = productRepository.findByIdWithPessimisticLock(
                        item.getProduct().getProductId()
                ).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

                if (product.getStatus() == ProductStatus.SOLD_OUT
                        || product.getStatus() == ProductStatus.INACTIVE) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (product.getStock() != null && product.getStock() < item.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (product.getStock() != null) {
                    int stockBefore = product.getStock();
                    product.decreaseStock(item.getQuantity());
                    int stockAfter = product.getStock();

                    // 임계값을 막 넘어선 순간에만 1회 발행 (주문마다 반복 알림 방지)
                    if (stockBefore > LOW_STOCK_THRESHOLD && stockAfter <= LOW_STOCK_THRESHOLD) {
                        eventPublisher.publishEvent(new ProductStockWarningEvent(
                                product.getStore().getAccount().getAccountId(),
                                product.getStore().getName(),
                                product.getName(),
                                stockAfter,
                                product.getProductId()
                        ));
                    }
                }
            }

            if (item.getEventProduct() != null) {
                EventProduct eventProduct = eventProductRepository.findByIdWithPessimisticLock(
                        item.getEventProduct().getEventProductId()
                ).orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

                if (!eventProduct.isOngoing()) {
                    throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
                }

                if (eventProduct.getRemainingStock() < item.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                eventProduct.decreaseStock(item.getQuantity());
            }
        }
    }

    private void restoreStock(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            if (item.getEventProductId() != null) {
                EventProduct eventProduct = eventProductRepository
                        .findByIdWithPessimisticLock(item.getEventProductId())
                        .orElse(null);

                if (eventProduct != null) {
                    eventProduct.restoreStock(item.getQuantity());
                }

                continue;
            }

            if (item.getProductId() != null) {
                // 차감 경로(주문 생성)가 비관적 락으로 재고를 수정하므로 복원도 같은 락으로 읽어야
                // lost update(스테일 값 기준 복원으로 재고 부풀림 → oversell)를 막을 수 있다.
                Product product = productRepository
                        .findByIdWithPessimisticLock(item.getProductId())
                        .orElse(null);

                if (product != null && product.getStock() != null) {
                    product.restoreStock(item.getQuantity());
                }
            }
        }
    }

    private String resolveThumbnailUrl(CartItem item) {
        Long productId = item.getProduct() != null
                ? item.getProduct().getProductId()
                : item.getEventProduct().getProduct().getProductId();

        return productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .filter(image -> image.isThumbnail())
                .findFirst()
                .map(image -> image.getImageUrl())
                .orElse(null);
    }

    private String createPaymentId(String orderNumber, PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.CASH_ON_SITE) {
            return null;
        }

        return "pay_" + orderNumber.replace("-", "_");
    }

    private String createOrderName(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "이음 주문";
        }

        String firstName = items.get(0).getProductName();

        if (items.size() == 1) {
            return firstName;
        }

        return firstName + " 외 " + (items.size() - 1) + "건";
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String randomPart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return "ORD-" + datePart + "-" + randomPart;
    }

    private void cancelOrderWithLock(Long accountId, Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        Payment payment = paymentRepository
                .findByOrderIdWithPessimisticLock(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        /*
         * 현재 단계에서는 PENDING 주문만 취소 허용.
         * PAID 주문은 PortOne 취소 API 구현 후 허용하는 게 안전함.
         */
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.NOT_PAID) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        List<OrderItem> orderItems = orderItemRepository
                .findByOrder_OrderId(order.getOrderId());

        restoreStock(orderItems);

        order.cancel("사용자 요청");
        payment.cancel();

        log.info("주문 취소 완료: orderId={}, paymentStatus={}",
                order.getOrderId(), payment.getStatus());
    }

    private void expirePendingOrderWithLock(Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Payment payment = paymentRepository
                .findByOrderIdWithPessimisticLock(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        /*
         * 온라인 결제 대기 주문만 만료 처리.
         * 현장결제는 PaymentStatus.NOT_PAID이므로 만료 대상 아님.
         */
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        List<OrderItem> orderItems = orderItemRepository
                .findByOrder_OrderId(order.getOrderId());

        restoreStock(orderItems);

        order.expire();
        payment.cancel();

        log.info("결제 대기 주문 만료 처리 완료: orderId={}", order.getOrderId());
    }


    private OrderResponseDto toOrderDto(
            Order order,
            List<OrderItem> items,
            Payment payment,
            boolean hasReview
    ) {
        return OrderResponseDto.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .storeId(order.getStore().getStoreId())
                .storeName(order.getStore().getName())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .paymentStatus(payment != null ? payment.getStatus().name() : null)
                .paymentMethod(payment != null ? payment.getPaymentMethod().name() : null)
                .totalPrice(order.getTotalPrice())
                .items(items.stream().map(this::toOrderItemDto).toList())
                .pickupScheduledAt(order.getPickupScheduledAt())
                .requestMessage(order.getRequestMessage())
                .paidAt(order.getPaidAt())
                .confirmedAt(order.getConfirmedAt())
                .readyAt(order.getReadyAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .createdAt(order.getCreatedAt())
                .hasReview(hasReview)
                .build();
    }

    private OrderItemResponseDto toOrderItemDto(OrderItem item) {
        return OrderItemResponseDto.builder()
                .orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .eventProductId(item.getEventProductId())
                .productType(item.getProductType().name())
                .productName(item.getProductName())
                .thumbnailUrl(fileStorageService.resolveImageUrl(item.getThumbnailUrl()))
                .selectedOptionsText(item.getSelectedOptionsText())
                .basePrice(item.getBasePrice())
                .optionsTotalPrice(item.getOptionsTotalPrice())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotalPrice(item.getLineTotalPrice())
                .build();
    }
}