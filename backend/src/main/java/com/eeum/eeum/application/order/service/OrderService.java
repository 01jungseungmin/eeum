package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.OrderCreateRequestDto;
import com.eeum.eeum.application.order.dto.response.OrderItemResponseDto;
import com.eeum.eeum.application.order.dto.response.OrderPaymentReadyResponseDto;
import com.eeum.eeum.application.order.dto.response.OrderResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.entity.CartItem;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.OrderItem;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.CartItemRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderItemRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountRepository accountRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final ProductImageRepository productImageRepository;

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

        validateStock(cartItems);

        decreaseStock(cartItems);

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
        return orderRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(order -> {
                    List<OrderItem> items = orderItemRepository
                            .findByOrder_OrderId(order.getOrderId());

                    Payment payment = paymentRepository
                            .findByOrder_OrderId(order.getOrderId())
                            .orElse(null);

                    return toOrderDto(order, items, payment);
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

        return toOrderDto(order, items, payment);
    }

    @Transactional
    public void cancelOrder(Long accountId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getAccount().getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(orderId);

        restoreStock(orderItems);

        order.cancel("사용자 요청");

        Payment payment = paymentRepository
                .findByOrder_OrderId(orderId)
                .orElse(null);

        if (payment != null && payment.getStatus() != PaymentStatus.PAID) {
            payment.cancel();
        }

        log.info("주문 취소: orderId={}", orderId);
    }

    @Transactional
    public void expirePendingOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        List<OrderItem> orderItems = orderItemRepository
                .findByOrder_OrderId(order.getOrderId());

        restoreStock(orderItems);

        order.expire();

        Payment payment = paymentRepository
                .findByOrder_OrderId(order.getOrderId())
                .orElse(null);

        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.cancel();
        }

        log.info("결제 대기 주문 만료 처리: orderId={}", order.getOrderId());
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

    private void validateStock(List<CartItem> cartItems) {
        for (CartItem item : cartItems) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();

                if (product.getStatus() == ProductStatus.SOLD_OUT
                        || product.getStatus() == ProductStatus.INACTIVE) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (product.getStock() != null && product.getStock() < item.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }
            }

            if (item.getEventProduct() != null) {
                EventProduct eventProduct = item.getEventProduct();

                if (!eventProduct.isOngoing()) {
                    throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
                }

                if (eventProduct.getRemainingStock() < item.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }
            }
        }
    }

    private void decreaseStock(List<CartItem> cartItems) {
        for (CartItem item : cartItems) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();

                if (product.getStock() != null) {
                    product.decreaseStock(item.getQuantity());
                }
            }

            if (item.getEventProduct() != null) {
                item.getEventProduct().decreaseStock(item.getQuantity());
            }
        }
    }

    private void restoreStock(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            if (item.getEventProductId() != null) {
                EventProduct eventProduct = eventProductRepository
                        .findById(item.getEventProductId())
                        .orElse(null);

                if (eventProduct != null) {
                    eventProduct.getProduct().restoreStock(item.getQuantity());
                }

                continue;
            }

            if (item.getProductId() != null) {
                Product product = productRepository
                        .findById(item.getProductId())
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

    private OrderResponseDto toOrderDto(
            Order order,
            List<OrderItem> items,
            Payment payment
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
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderItemResponseDto toOrderItemDto(OrderItem item) {
        return OrderItemResponseDto.builder()
                .orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .eventProductId(item.getEventProductId())
                .productType(item.getProductType().name())
                .productName(item.getProductName())
                .thumbnailUrl(item.getThumbnailUrl())
                .selectedOptionsText(item.getSelectedOptionsText())
                .basePrice(item.getBasePrice())
                .optionsTotalPrice(item.getOptionsTotalPrice())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotalPrice(item.getLineTotalPrice())
                .build();
    }
}