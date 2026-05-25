package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.ProductCreateRequestDto;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void createProduct(Long accountId, ProductCreateRequestDto request) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        if (request.getProductType() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        BigDecimal price = request.getBasePrice() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(request.getBasePrice());

        String description = request.getDescription() == null
                ? ""
                : request.getDescription();

        Product product = Product.create(
                store,
                request.getName(),
                description,
                price,
                request.getStockQuantity(),
                request.getProductType()
        );

        productRepository.save(product);
    }
}