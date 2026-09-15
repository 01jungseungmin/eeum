package com.eeum.eeum.application.product.mapper;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.product.dto.response.*;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.domain.product.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final FileStorageService fileStorageService;

    private Integer calculateDiscountRate(BigDecimal originalPrice, BigDecimal eventPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        return originalPrice.subtract(eventPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public EventProductResponseDto toEventProductResponseDto(EventProduct eventProduct) {
        Product product = eventProduct.getProduct();

        return EventProductResponseDto.builder()
                .eventProductId(eventProduct.getEventProductId())
                .productId(product.getProductId())
                .productName(product.getName())
                .originalPrice(product.getPrice())
                .eventPrice(eventProduct.getEventPrice())
                .discountRate(calculateDiscountRate(product.getPrice(), eventProduct.getEventPrice()))
                .eventStock(eventProduct.getEventStock())
                .soldCount(eventProduct.getSoldCount())
                .remainingStock(eventProduct.getRemainingStock())
                .startAt(eventProduct.getStartAt())
                .endAt(eventProduct.getEndAt())
                .ongoing(eventProduct.isOngoing())
                .eventStatus(eventProduct.resolveDisplayStatus())
                .build();
    }

    public ProductCategoryResponseDto toProductCategoryResponseDto(ProductCategory category, int productCount) {
        return ProductCategoryResponseDto.builder()
                .productCategoryId(category.getProductCategoryId())
                .storeId(category.getStore().getStoreId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.isActive())
                .productCount(productCount)
                .build();
    }

    public ImageResponseDto toImageResponseDto(ProductImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getProductImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }

    public ProductOptionDto toProductOptionDto(ProductOption option, List<ProductOptionItem> items) {
        return ProductOptionDto.builder()
                .optionId(option.getProductOptionId())
                .groupName(option.getGroupName())
                .selectionType(option.getSelectionType())
                .isRequired(option.isRequired())
                .displayOrder(option.getDisplayOrder())
                .items(toProductOptionItemDtos(items))
                .build();
    }

    private List<ProductOptionItemDto> toProductOptionItemDtos(List<ProductOptionItem> items) {
        return items.stream()
                .map(this::toProductOptionItemDto)
                .toList();
    }

    private ProductOptionItemDto toProductOptionItemDto(ProductOptionItem item) {
        return ProductOptionItemDto.builder()
                .itemId(item.getProductOptionItemId())
                .itemName(item.getItemName())
                .additionalPrice(item.getAdditionalPrice())
                .isDefault(item.isDefault())
                .displayOrder(item.getDisplayOrder())
                .isAvailable(item.isAvailable())
                .build();
    }

    public ProductResponseDto toProductResponseDto(Product product) {
        ProductCategory category = product.getProductCategory();

        return ProductResponseDto.builder()
                .productId(product.getProductId())
                .storeId(product.getStore().getStoreId())
                .categoryId(category.getProductCategoryId())
                .categoryName(category.getName())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .productType(product.getProductType().name())
                .status(product.getStatus().name())
                .viewCount(product.getViewCount())
                .createdAt(product.getCreatedAt())
                .modifiedAt(product.getModifiedAt())
                .build();
    }
}
