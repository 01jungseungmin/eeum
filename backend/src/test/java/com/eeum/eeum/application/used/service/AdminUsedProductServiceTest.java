package com.eeum.eeum.application.used.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 숨김 해제 테스트.
 * 숨김 해제와 신고 조치(숨김)는 같은 컬럼을 다투므로 같은 잠금 규약을 써야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminUsedProductServiceTest {

    private static final Long PRODUCT_ID = 10L;

    @Mock private UsedProductRepository usedProductRepository;

    @InjectMocks
    private AdminUsedProductService adminUsedProductService;

    @Test
    void 숨김_해제는_신고_조치와_같은_비관적_잠금으로_게시글을_읽는다() {
        // given — 잠금 없이 읽으면 "숨김 상태인지" 확인과 해제 사이에 신고 조치가 끼어들 수 있다
        UsedProduct hidden = product();
        hidden.hide();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(hidden));

        // when
        adminUsedProductService.show(PRODUCT_ID);

        // then
        assertThat(hidden.isHidden()).isFalse();
        verify(usedProductRepository, never()).findByUsedProductIdAndDeletedAtIsNull(any());
    }

    @Test
    void 삭제된_게시글은_숨김_해제할_수_없다() {
        // given — 잠금 조회는 삭제 필터가 없으므로 서비스에서 걸러야 한다
        UsedProduct deleted = product();
        deleted.hide();
        deleted.softDelete();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> adminUsedProductService.show(PRODUCT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 숨김_상태가_아니면_해제할_수_없다() {
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> adminUsedProductService.show(PRODUCT_ID))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_HIDDEN);
    }

    private UsedProduct product() {
        Account seller = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        UsedProduct product = UsedProduct.create(
                seller, Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }
}
