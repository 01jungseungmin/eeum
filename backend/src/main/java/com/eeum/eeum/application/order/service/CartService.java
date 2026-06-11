package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.order.dto.request.CartItemAddRequestDto;
import com.eeum.eeum.application.order.dto.request.CartItemUpdateRequestDto;
import com.eeum.eeum.application.order.dto.response.CartItemResponseDto;
import com.eeum.eeum.application.order.dto.response.CartResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Cart;
import com.eeum.eeum.domain.order.entity.CartItem;
import com.eeum.eeum.domain.order.repository.CartItemRepository;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.product.entity.*;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.*;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final AccountRepository accountRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final EventProductRepository eventProductRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionItemRepository productOptionItemRepository;
    private final ProductOptionRepository productOptionRepository;

    @Transactional
    public CartResponseDto getCart(Long accountId) {
        Cart cart = getOrCreateCart(accountId);
        List<CartItem> items = cartItemRepository.findByCart_CartId(cart.getCartId());

        BigDecimal totalPrice = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponseDto.builder()
                .cartId(cart.getCartId())
                .storeId(cart.getStore() != null ? cart.getStore().getStoreId() : null)
                .storeName(cart.getStore() != null ? cart.getStore().getName() : null)
                .items(items.stream().map(this::toCartItemDto).toList())
                .totalPrice(totalPrice)
                .build();
    }

    @Transactional
    public CartResponseDto addItem(Long accountId, CartItemAddRequestDto request) {
        validateRequest(request);

        Cart cart = getOrCreateCart(accountId);

        if (request.getProductId() != null) {
            addProductToCart(cart, request);
        } else {
            addEventProductToCart(cart, request);
        }

        return getCart(accountId);
    }

    @Transactional
    public CartResponseDto updateItem(
            Long accountId,
            Long cartItemId,
            CartItemUpdateRequestDto request
    ) {
        Cart cart = getOrCreateCart(accountId);

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BusinessException(ErrorCode.CART_ACCESS_DENIED);
        }

        validateQuantityLimit(item, request.getQuantity());

        item.updateQuantity(request.getQuantity());

        return getCart(accountId);
    }

    @Transactional
    public CartResponseDto removeItem(Long accountId, Long cartItemId) {
        Cart cart = getOrCreateCart(accountId);

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BusinessException(ErrorCode.CART_ACCESS_DENIED);
        }

        cartItemRepository.delete(item);

        List<CartItem> remaining = cartItemRepository.findByCart_CartId(cart.getCartId());
        if (remaining.isEmpty()) {
            cart.clear();
        }

        return getCart(accountId);
    }

    @Transactional
    public void clearCart(Long accountId) {
        Cart cart = getOrCreateCart(accountId);
        cartItemRepository.deleteByCart_CartId(cart.getCartId());
        cart.clear();
    }

    // ===================== 내부 유틸 =====================

    private void addProductToCart(Cart cart, CartItemAddRequestDto request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validatePurchasableProduct(product);

        validateCartStore(cart, product.getStore());

        validateCartProductType(cart, product.getProductType());

        OptionSelectionResult optionResult = resolveOptions(
                product.getProductId(),
                request.getSelectedOptionItemIds()
        );

        cartItemRepository
                .findByCart_CartIdAndProduct_ProductIdAndSelectedOptionsHash(
                        cart.getCartId(),
                        product.getProductId(),
                        optionResult.optionsHash()
                )
                .ifPresentOrElse(
                        existing -> {
                            int nextQuantity = existing.getQuantity() + request.getQuantity();
                            validateQuantityLimit(existing, nextQuantity);
                            existing.updateQuantity(nextQuantity);
                        },
                        () -> {
                            validateProductStock(product, request.getQuantity());

                            CartItem item = CartItem.createForProduct(
                                    cart,
                                    product,
                                    optionResult.selectedOptionItemIds(),
                                    optionResult.selectedOptionsText(),
                                    optionResult.optionsTotalPrice(),
                                    optionResult.optionsHash(),
                                    request.getQuantity()
                            );

                            cartItemRepository.save(item);
                        }
                );
    }

    private void addEventProductToCart(Cart cart, CartItemAddRequestDto request) {
        EventProduct eventProduct = eventProductRepository.findById(request.getEventProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (!eventProduct.isOngoing()) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }

        Product product = eventProduct.getProduct();

        if (product.getStatus() == ProductStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        validateCartStore(cart, product.getStore());

        validateCartProductType(cart, ProductType.SALE);

        cartItemRepository
                .findByCart_CartIdAndEventProduct_EventProductId(
                        cart.getCartId(),
                        eventProduct.getEventProductId()
                )
                .ifPresentOrElse(
                        existing -> {
                            int nextQuantity = existing.getQuantity() + request.getQuantity();

                            if (eventProduct.getRemainingStock() < nextQuantity) {
                                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                            }

                            existing.updateQuantity(nextQuantity);
                        },
                        () -> {
                            if (eventProduct.getRemainingStock() < request.getQuantity()) {
                                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
                            }

                            cartItemRepository.save(
                                    CartItem.createForEventProduct(
                                            cart,
                                            eventProduct,
                                            request.getQuantity()
                                    )
                            );
                        }
                );
    }

    private void validateRequest(CartItemAddRequestDto request) {
        if (request.getProductId() == null && request.getEventProductId() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (request.getProductId() != null && request.getEventProductId() != null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private void validatePurchasableProduct(Product product) {
        if (product.getProductType() == ProductType.MENU) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        }

        if (product.getStatus() == ProductStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (product.getStatus() == ProductStatus.SOLD_OUT) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }

    private void validateCartStore(Cart cart, com.eeum.eeum.domain.store.entity.Store store) {
        Long storeId = store.getStoreId();

        if (!cart.isEmpty() && !cart.isSameStore(storeId)) {
            cartItemRepository.deleteByCart_CartId(cart.getCartId());
            cart.updateStore(store);
            return;
        }

        if (cart.isEmpty()) {
            cart.updateStore(store);
        }
    }

    private void validateCartProductType(Cart cart, ProductType newProductType) {
        List<CartItem> items = cartItemRepository.findByCart_CartId(cart.getCartId());

        if (items.isEmpty()) {
            return;
        }

        ProductType existingType = resolveCartProductType(items.get(0));

        if (existingType != newProductType) {
            throw new BusinessException(ErrorCode.ORDER_TYPE_MISMATCH);
        }
    }

    private ProductType resolveCartProductType(CartItem item) {
        if (item.getProduct() != null) {
            return item.getProduct().getProductType();
        }

        return ProductType.SALE;
    }

    private void validateProductStock(Product product, int quantity) {
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }

    private void validateQuantityLimit(CartItem item, int quantity) {
        if (item.getProduct() != null) {
            validateProductStock(item.getProduct(), quantity);
        }

        if (item.getEventProduct() != null
                && item.getEventProduct().getRemainingStock() < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }

    private OptionSelectionResult resolveOptions(
            Long productId,
            List<Long> selectedOptionItemIds
    ) {
        List<Long> sortedIds = selectedOptionItemIds == null
                ? List.of()
                : selectedOptionItemIds.stream()
                .distinct()
                .sorted()
                .toList();

        List<ProductOption> productOptions =
                productOptionRepository.findByProduct_ProductId(productId);

        validateRequiredOptions(productOptions, sortedIds);

        if (sortedIds.isEmpty()) {
            return new OptionSelectionResult(
                    "",
                    null,
                    BigDecimal.ZERO,
                    "none"
            );
        }

        List<ProductOptionItem> optionItems =
                productOptionItemRepository
                        .findByProductOptionItemIdInAndProductOption_Product_ProductId(
                                sortedIds,
                                productId
                        );

        if (optionItems.size() != sortedIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        for (ProductOptionItem item : optionItems) {
            if (!item.isAvailable()) {
                throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
            }
        }

        validateRequiredOptionsSelected(productOptions, optionItems);

        BigDecimal optionsTotalPrice = optionItems.stream()
                .map(ProductOptionItem::getAdditionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String selectedOptionItemIdsText = sortedIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String selectedOptionsText = optionItems.stream()
                .sorted(Comparator.comparing(ProductOptionItem::getDisplayOrder))
                .map(item -> item.getProductOption().getGroupName()
                        + ": "
                        + item.getItemName())
                .collect(Collectors.joining(", "));

        String hash = generateOptionsHash(selectedOptionItemIdsText);

        return new OptionSelectionResult(
                selectedOptionItemIdsText,
                selectedOptionsText,
                optionsTotalPrice,
                hash
        );
    }

    private String generateOptionsHash(String selectedOptionItemIds) {
        if (selectedOptionItemIds == null || selectedOptionItemIds.isBlank()) {
            return "none";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(
                    selectedOptionItemIds.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
    }

    private Cart getOrCreateCart(Long accountId) {
        return cartRepository.findByAccount_AccountId(accountId)
                .orElseGet(() -> {
                    Account account = accountRepository.findById(accountId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.ACCOUNT_NOT_FOUND
                            ));

                    return cartRepository.save(Cart.create(account));
                });
    }

    private CartItemResponseDto toCartItemDto(CartItem item) {
        Long productId = null;
        Long eventProductId = null;
        String productName;
        String thumbnailUrl;
        BigDecimal basePrice;
        boolean isEventProduct = false;

        if (item.getProduct() != null) {
            productId = item.getProduct().getProductId();
            productName = item.getProduct().getName();
            basePrice = item.getProduct().getPrice();
            thumbnailUrl = getProductThumbnailUrl(productId);
        } else {
            eventProductId = item.getEventProduct().getEventProductId();
            productId = item.getEventProduct().getProduct().getProductId();
            productName = item.getEventProduct().getProduct().getName();
            basePrice = item.getEventProduct().getProduct().getPrice();
            thumbnailUrl = getProductThumbnailUrl(productId);
            isEventProduct = true;
        }

        return CartItemResponseDto.builder()
                .cartItemId(item.getCartItemId())
                .productId(productId)
                .eventProductId(eventProductId)
                .productName(productName)
                .thumbnailUrl(thumbnailUrl)
                .selectedOptionsText(item.getSelectedOptionsText())
                .basePrice(basePrice)
                .optionsTotalPrice(item.getOptionsTotalPrice())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .totalPrice(item.getTotalPrice())
                .isEventProduct(isEventProduct)
                .build();
    }

    private String getProductThumbnailUrl(Long productId) {
        return productImageRepository
                .findByProduct_ProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .filter(ProductImage::isThumbnail)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private void validateRequiredOptions(
            List<ProductOption> productOptions,
            List<Long> selectedOptionItemIds
    ) {
        boolean hasRequiredOption = productOptions.stream()
                .anyMatch(ProductOption::isRequired);

        if (hasRequiredOption && selectedOptionItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_REQUIRED_OPTION_MISSING);
        }
    }

    private void validateRequiredOptionsSelected(
            List<ProductOption> productOptions,
            List<ProductOptionItem> selectedItems
    ) {
        for (ProductOption option : productOptions) {
            if (!option.isRequired()) {
                continue;
            }

            boolean selected = selectedItems.stream()
                    .anyMatch(item -> item.getProductOption()
                            .getProductOptionId()
                            .equals(option.getProductOptionId()));

            if (!selected) {
                throw new BusinessException(ErrorCode.PRODUCT_REQUIRED_OPTION_MISSING);
            }
        }
    }

    private record OptionSelectionResult(
            String selectedOptionItemIds,
            String selectedOptionsText,
            BigDecimal optionsTotalPrice,
            String optionsHash
    ) {
    }
}