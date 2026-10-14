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
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.order.entity.*;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.event.OrderPlacedEvent;
import com.eeum.eeum.domain.order.repository.*;
import com.eeum.eeum.domain.product.entity.EventProduct;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.entity.ProductOption;
import com.eeum.eeum.domain.product.entity.ProductOptionItem;
import com.eeum.eeum.domain.product.enums.OptionSelectionType;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.event.ProductStockWarningEvent;
import com.eeum.eeum.domain.product.repository.EventProductRepository;
import com.eeum.eeum.domain.product.repository.ProductImageRepository;
import com.eeum.eeum.domain.product.repository.ProductOptionRepository;
import com.eeum.eeum.domain.product.repository.ProductOptionItemRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final FileStorageService fileStorageService;
    private final AccountRepository accountRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final CartRepository cartRepository;
    private final com.eeum.eeum.domain.store.repository.StoreRepository storeRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionItemRepository productOptionItemRepository;
    private final EventProductRepository eventProductRepository;
    private final EntityManager entityManager;
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
        validateSupportedPaymentMethod(request.getPaymentMethod());

        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        account.assertWritable();

        Cart cart = cartRepository.findByAccountIdWithPessimisticLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findByCart_CartId(cart.getCartId());

        // 빈 카트는 상점도 비어 있다(주문 완료 후 비워진 카트 포함).
        // 상점을 먼저 읽으면 NPE로 500이 나가므로 빈 카트를 먼저 판정한다.
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        var store = storeRepository.findByIdWithPessimisticLock(cart.getStore().getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() != com.eeum.eeum.domain.store.enums.StoreStatus.OPEN) {
            throw new BusinessException(ErrorCode.STORE_CLOSED);
        }

        // 사장 정지는 Account만 바꾸고 상점은 OPEN으로 남는다. 판매자 계정을 상점 행 다음에
        // 잠가 정지 커밋과 순서를 정한다 — 잠금 순서는 구매자 account → cart → store → 판매자 account다.
        Account seller = accountRepository.findByIdWithLock(store.getAccount().getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!seller.isActive()) {
            throw new BusinessException(ErrorCode.STORE_SUSPENDED);
        }
        // 공개 목록에서 숨긴 미승인 상점은 기존 장바구니를 통한 주문도 받지 않는다.
        OwnerInfo ownerInfo = ownerInfoRepository.findByAccountIdWithLock(seller.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        entityManager.refresh(ownerInfo, LockModeType.PESSIMISTIC_WRITE);
        if (ownerInfo.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }

        validateAndDecreaseStock(cartItems);

        OrderType orderType = resolveOrderType(cartItems);
        validatePickupPolicy(orderType, request.getPickupScheduledAt());

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

        if (payment.getStatus() != PaymentStatus.PAID
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }

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

    /**
     * 담을 때 고정한 단가가 지금도 유효한지 확인한다. 가격 보장 기간은 두지 않는다 —
     * 옛 금액으로 결제되면 영수증·수익 원장과 어긋나고, 말없이 새 금액을 청구할 수도 없다.
     * 삭제·품절된 옵션도 여기서 걸러 결제 전에 장바구니를 고치게 한다.
     */
    private void validateUnitPriceUnchanged(CartItem item, Product product) {
        List<Long> optionItemIds = parseSelectedOptionItemIds(item.getSelectedOptionItemIds());
        List<ProductOption> productOptions = productOptionRepository
                .findByProduct_ProductId(product.getProductId());

        BigDecimal optionsTotalPrice = BigDecimal.ZERO;
        List<ProductOptionItem> optionItems = List.of();
        if (!optionItemIds.isEmpty()) {
            optionItems = productOptionItemRepository
                    .findByProductOptionItemIdInAndProductOption_Product_ProductId(
                            optionItemIds, product.getProductId());
            if (optionItems.size() != optionItemIds.size()
                    || optionItems.stream().anyMatch(optionItem -> !optionItem.isAvailable())) {
                throw new BusinessException(ErrorCode.ORDER_OPTION_UNAVAILABLE);
            }
            optionsTotalPrice = optionItems.stream()
                    .map(ProductOptionItem::getAdditionalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        validateRequiredOptionsSelected(productOptions, optionItems);

        if (product.getPrice().add(optionsTotalPrice).compareTo(item.getUnitPrice()) != 0) {
            throw new BusinessException(ErrorCode.ORDER_PRICE_CHANGED);
        }
    }

    private void validateRequiredOptionsSelected(
            List<ProductOption> productOptions,
            List<ProductOptionItem> selectedOptionItems
    ) {
        boolean missingRequiredOption = productOptions.stream()
                .filter(ProductOption::isRequired)
                .anyMatch(option -> selectedOptionItems.stream()
                        .noneMatch(item -> item.getProductOption().getProductOptionId()
                                .equals(option.getProductOptionId())));
        if (missingRequiredOption) {
            throw new BusinessException(ErrorCode.ORDER_OPTION_UNAVAILABLE);
        }
        boolean invalidSingleSelection = productOptions.stream()
                .filter(option -> option.getSelectionType() == OptionSelectionType.SINGLE)
                .anyMatch(option -> selectedOptionItems.stream()
                        .filter(item -> item.getProductOption().getProductOptionId()
                                .equals(option.getProductOptionId()))
                        .count() > 1);
        if (invalidSingleSelection) {
            throw new BusinessException(ErrorCode.ORDER_OPTION_UNAVAILABLE);
        }
    }

    private List<Long> parseSelectedOptionItemIds(String selectedOptionItemIds) {
        if (selectedOptionItemIds == null || selectedOptionItemIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(selectedOptionItemIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private void validateAndDecreaseStock(List<CartItem> cartItems) {
        Map<Long, Product> lockedProducts = new HashMap<>();
        Map<Long, EventProduct> lockedEvents = new HashMap<>();
        // 일반/이벤트가 같은 Product.stock을 사용한다. 모든 주문이 Product ID 순으로
        // 잠가야 서로 다른 장바구니의 상품 순서가 달라도 교착하지 않는다.
        for (CartStockLine line : cartItems.stream()
                .map(item -> new CartStockLine(item, stockProductId(item)))
                .sorted(Comparator.comparing(CartStockLine::productId))
                .toList()) {
            CartItem item = line.item();
            if (item.getProduct() != null) {
                Product product = lockedProducts.computeIfAbsent(line.productId(),
                        this::lockAndRefreshProduct);

                if (product.getStatus() == ProductStatus.SOLD_OUT
                        || product.getStatus() == ProductStatus.INACTIVE
                        || !isActiveProductCategory(product)) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (product.getStock() != null && product.getStock() < item.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                validateUnitPriceUnchanged(item, product);

                decreasePhysicalStock(product, item.getQuantity());
                flushStockChange(ErrorCode.PRODUCT_OUT_OF_STOCK);
            }

            if (item.getEventProduct() != null) {
                // EventProduct보다 Product를 먼저 잠근다. 일반 주문도 이 행을 잠그므로
                // 두 판매 경로의 실재고 검증/차감이 같은 직렬화 지점을 공유한다.
                Product product = lockedProducts.computeIfAbsent(line.productId(),
                        this::lockAndRefreshProduct);
                EventProduct eventProduct = lockedEvents.computeIfAbsent(
                        item.getEventProduct().getEventProductId(), this::lockAndRefreshEvent);

                if (!eventProduct.isOngoing()) {
                    throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
                }
                if (!eventProduct.getProduct().getProductId().equals(product.getProductId())
                        || product.getStatus() != ProductStatus.ACTIVE
                        || !isActiveProductCategory(product)) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (eventProduct.getRemainingStock() < item.getQuantity()
                        || (product.getStock() != null && product.getStock() < item.getQuantity())) {
                    throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                }

                if (eventProduct.getEventPrice().compareTo(item.getUnitPrice()) != 0) {
                    throw new BusinessException(ErrorCode.ORDER_PRICE_CHANGED);
                }

                decreasePhysicalStock(product, item.getQuantity());
                flushStockChange(ErrorCode.PRODUCT_OUT_OF_STOCK);
                eventProduct.decreaseStock(item.getQuantity());
                // 충돌을 트랜잭션 종료 시점까지 미루면 API가 낙관적 잠금 예외(500)를
                // 그대로 반환한다. 지금 확정해 롤백 가능한 도메인 오류로 변환한다.
                flushStockChange(ErrorCode.EVENT_NOT_FOUND);
            }
        }
    }

    private void flushStockChange(ErrorCode conflictCode) {
        try {
            entityManager.flush();
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException conflict) {
            throw new BusinessException(conflictCode);
        }
    }

    private Long stockProductId(CartItem item) {
        return item.getProduct() != null
                ? item.getProduct().getProductId()
                : eventProductRepository.findProductIdByEventProductId(
                        item.getEventProduct().getEventProductId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    private record CartStockLine(CartItem item, Long productId) {}

    private boolean isActiveProductCategory(Product product) {
        // 운영 DB의 product_category_id는 NOT NULL이다. null은 DB를 쓰지 않는 단위
        // 테스트 fixture에서만 나타나므로 기존 상품 검증과 호환한다.
        ProductCategory category = product.getProductCategory();
        if (category == null) {
            return true;
        }
        // 앞선 장바구니 조회가 만든 RR 스냅샷/영속성 캐시 대신 현재 상태를 읽는다.
        entityManager.refresh(category, LockModeType.PESSIMISTIC_WRITE);
        return category.isActive();
    }

    private Product lockAndRefreshProduct(Long productId) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        return product;
    }

    private EventProduct lockAndRefreshEvent(Long eventProductId) {
        EventProduct event = eventProductRepository.findByIdWithPessimisticLock(eventProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        entityManager.refresh(event, LockModeType.PESSIMISTIC_WRITE);
        return event;
    }

    private void decreasePhysicalStock(Product product, int quantity) {
        if (product.getStock() == null) {
            return;
        }
        int before = product.getStock();
        product.decreaseStock(quantity);
        int after = product.getStock();
        if (before > LOW_STOCK_THRESHOLD && after <= LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new ProductStockWarningEvent(
                    product.getStore().getAccount().getAccountId(),
                    product.getStore().getName(), product.getName(), after, product.getProductId()));
        }
    }

    private void restoreStock(List<OrderItem> orderItems) {
        Map<Long, Product> lockedProducts = new HashMap<>();
        Map<Long, EventProduct> lockedEvents = new HashMap<>();
        // 주문 항목의 eventProductId는 스냅샷 scalar라 상품 ID를 먼저 읽는다.
        // 읽기는 잠금이 아니며, 실제 변경은 아래 Product → EventProduct 순서로 잠근다.
        List<StockRestoreLine> lines = orderItems.stream()
                .map(item -> new StockRestoreLine(item, item.getProductId() != null
                        ? item.getProductId()
                        : eventProductRepository.findProductIdByEventProductId(item.getEventProductId())
                                .orElse(null)))
                .filter(line -> line.productId() != null)
                .sorted(Comparator.comparing(StockRestoreLine::productId))
                .toList();
        for (StockRestoreLine line : lines) {
            OrderItem item = line.item();
            Product product = lockedProducts.get(line.productId());
            if (product == null) {
                product = productRepository.findByIdWithPessimisticLock(line.productId()).orElse(null);
                if (product != null) {
                    entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
                    lockedProducts.put(line.productId(), product);
                }
            }
            if (product == null) {
                continue;
            }
            if (item.getEventProductId() != null) {
                EventProduct eventProduct = lockedEvents.get(item.getEventProductId());
                if (eventProduct == null) {
                    eventProduct = eventProductRepository
                            .findByIdWithPessimisticLock(item.getEventProductId()).orElse(null);
                    if (eventProduct != null) {
                        entityManager.refresh(eventProduct, LockModeType.PESSIMISTIC_WRITE);
                        lockedEvents.put(item.getEventProductId(), eventProduct);
                    }
                }

                if (eventProduct != null) {
                    if (eventProductRepository.restoreSoldCountAtomically(
                            item.getEventProductId(), item.getQuantity()) != 1) {
                        throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
                    }
                    entityManager.refresh(eventProduct, LockModeType.PESSIMISTIC_WRITE);
                    restorePhysicalStock(product, item.getQuantity());
                }

                continue;
            }

            if (product.getStock() != null) {
                restorePhysicalStock(product, item.getQuantity());
            }
        }
    }

    private void restorePhysicalStock(Product product, int quantity) {
        if (product.getStock() == null) {
            return;
        }
        if (productRepository.restoreStockAtomically(product.getProductId(), quantity,
                ProductStatus.SOLD_OUT, ProductStatus.ACTIVE) != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
    }

    private record StockRestoreLine(OrderItem item, Long productId) {}

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

    /**
     * 가상계좌는 취소 시 고객의 환불 계좌를 PortOne에 넘겨야 하는데, 지금 API에는 그 계좌를
     * 받는 계약이 없다. 그대로 열어 두면 환불할 수 없는 결제가 정산 원장에 쌓이므로
     * 신규 주문 단계에서 막는다. 이미 만들어진 가상계좌 결제의 외부 취소 Webhook
     * 조정은 그대로 동작한다.
     */
    private void validateSupportedPaymentMethod(PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.VIRTUAL_ACCOUNT) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_METHOD_NOT_SUPPORTED);
        }
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
