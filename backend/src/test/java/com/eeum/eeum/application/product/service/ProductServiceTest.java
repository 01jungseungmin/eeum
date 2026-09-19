package com.eeum.eeum.application.product.service;

import com.eeum.eeum.application.product.dto.request.ProductUpdateRequestDto;
import com.eeum.eeum.application.product.mapper.ProductMapper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.product.entity.Product;
import com.eeum.eeum.domain.product.entity.ProductCategory;
import com.eeum.eeum.domain.product.enums.ProductType;
import com.eeum.eeum.domain.product.repository.ProductCategoryRepository;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks private ProductService productService;

    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private ProductMapper productMapper;

    private Store createStore() {
        Account owner = Account.createUser("owner@test.com", "pw", "사장", "owner", "010-0000-0000");
        ReflectionTestUtils.setField(owner, "accountId", 1L);
        Store store = Store.createForOwnerSignup(owner, "테스트상점", "서울시", "02-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", 10L);
        return store;
    }

    private ProductUpdateRequestDto updateRequest(String description, Long categoryId) {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();
        ReflectionTestUtils.setField(request, "productType", ProductType.SALE);
        ReflectionTestUtils.setField(request, "name", "수정된 소금빵");
        ReflectionTestUtils.setField(request, "basePrice", 3500);
        ReflectionTestUtils.setField(request, "description", description);
        ReflectionTestUtils.setField(request, "categoryId", categoryId);
        return request;
    }

    @Test
    void 상품_수정_시_설명과_카테고리를_보내지_않으면_기존_값을_유지한다() {
        // given
        Store store = createStore();
        ProductCategory category = ProductCategory.create(store, "베이커리", 1);
        Product product = Product.create(store, category, "소금빵", "기존 설명",
                BigDecimal.valueOf(3000), 10, ProductType.SALE);

        when(storeRepository.findByAccount_AccountId(1L)).thenReturn(Optional.of(store));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        // when
        productService.updateProduct(1L, 100L, updateRequest(null, null));

        // then
        assertThat(product.getDescription()).isEqualTo("기존 설명");
        assertThat(product.getProductCategory()).isSameAs(category);
        assertThat(product.getName()).isEqualTo("수정된 소금빵");
        assertThat(product.getPrice()).isEqualByComparingTo("3500");
        verify(productCategoryRepository, never()).findByProductCategoryIdAndStore_StoreId(anyLong(), any());
    }

    @Test
    void 상품_수정_시_빈_설명을_보내면_설명을_비운다() {
        // given
        Store store = createStore();
        ProductCategory category = ProductCategory.create(store, "베이커리", 1);
        Product product = Product.create(store, category, "소금빵", "기존 설명",
                BigDecimal.valueOf(3000), 10, ProductType.SALE);

        when(storeRepository.findByAccount_AccountId(1L)).thenReturn(Optional.of(store));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        // when
        productService.updateProduct(1L, 100L, updateRequest("", null));

        // then
        assertThat(product.getDescription()).isEmpty();
    }
}
