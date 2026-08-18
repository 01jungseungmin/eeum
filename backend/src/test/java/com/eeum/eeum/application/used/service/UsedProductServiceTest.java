package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsedProductServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CATEGORY_ID = 100L;
    private static final Long REGION_ID = 1000L;

    @Mock private UsedProductRepository usedProductRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRegionRepository accountRegionRepository;

    @InjectMocks
    private UsedProductService usedProductService;

    // ─────────────────── 등록 ───────────────────

    @Test
    void 인증된_활동_지역이면_게시글이_등록된다() {
        // given
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.of(accountRegion(true)));
        when(usedProductRepository.save(any(UsedProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UsedProductDetailResponseDto result =
                usedProductService.create(SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000")));

        // then
        assertThat(result.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(result.getRegionId()).isEqualTo(REGION_ID);
        assertThat(result.getPrice()).isEqualByComparingTo("10000");
    }

    @Test
    void 내_활동_지역이_아니면_등록할_수_없다() {
        // given — 검증이 없으면 아무 regionId나 실어 남의 동네에 글을 올릴 수 있다
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_ACCESS_REQUIRED);

        verify(usedProductRepository, never()).save(any());
    }

    @Test
    void GPS_인증이_안_된_지역에는_등록할_수_없다() {
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));
        when(accountRegionRepository.findByAccount_AccountIdAndRegion_RegionId(SELLER_ID, REGION_ID))
                .thenReturn(Optional.of(accountRegion(false)));

        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_NOT_VERIFIED);
    }

    @Test
    void 가게_카테고리로는_중고_게시글을_등록할_수_없다() {
        // given — 조회 자체를 CategoryType.USED로 걸어 가게·비활성 카테고리를 함께 막는다
        when(accountRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedProductService.create(
                SELLER_ID, createRequest(UsedProductPriceType.FIXED, new BigDecimal("10000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_CATEGORY);
    }

    // ─────────────────── 조회 ───────────────────

    @Test
    void 삭제된_게시글은_조회되지_않는다() {
        // given — 리포지토리 조회 자체가 deletedAt IS NULL을 포함한다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedProductService.getDetail(SELLER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 숨김_처리된_글은_작성자에게만_보인다() {
        // given
        UsedProduct product = product();
        product.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        // when & then — 작성자는 볼 수 있다
        assertThat(usedProductService.getDetail(SELLER_ID, PRODUCT_ID).isHidden()).isTrue();
    }

    @Test
    void 숨김_처리된_글을_남이_조회하면_숨김_사실을_알리지_않고_404다() {
        // given — 403을 주면 "여기 숨겨진 글이 있다"는 사실이 새어 나간다
        UsedProduct product = product();
        product.hide();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> usedProductService.getDetail(OTHER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    // ─────────────────── 수정 ───────────────────

    @Test
    void 작성자는_게시글을_수정할_수_있다() {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));

        UsedProductDetailResponseDto result = usedProductService.update(
                SELLER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.NEGOTIABLE, null));

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getPriceType()).isEqualTo(UsedProductPriceType.NEGOTIABLE);
        assertThat(result.getPrice()).isNull();
    }

    @Test
    void 수정_경로로도_가격_규칙을_우회할_수_없다() {
        // given — NEGOTIABLE인데 금액이 실려 오면 거부한다
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));
        when(categoryRepository.findByCategoryIdAndTypeAndIsActiveTrue(CATEGORY_ID, CategoryType.USED))
                .thenReturn(Optional.of(usedCategory()));

        assertThatThrownBy(() -> usedProductService.update(
                SELLER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.NEGOTIABLE, new BigDecimal("5000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_INVALID_PRICE);
    }

    @Test
    void 남의_게시글은_수정할_수_없다() {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> usedProductService.update(
                OTHER_ID, PRODUCT_ID, updateRequest(UsedProductPriceType.FIXED, new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    // ─────────────────── 삭제 ───────────────────

    @Test
    void 삭제하면_물리_삭제_대신_deletedAt이_찍힌다() {
        // given — 후기·채팅·신고 이력이 가리킬 대상을 남겨야 한다
        UsedProduct product = product();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        // when
        usedProductService.delete(SELLER_ID, PRODUCT_ID);

        // then
        assertThat(product.isDeleted()).isTrue();
        verify(usedProductRepository, never()).delete(any());
        verify(usedProductRepository, never()).deleteById(eq(PRODUCT_ID));
    }

    @Test
    void 예약_중인_게시글은_삭제할_수_없다() {
        // given — 상대가 거래를 기다리는 중이다. 말없이 사라지면 이유를 알 수 없다.
        UsedProduct product = product();
        product.reserve();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> usedProductService.delete(SELLER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_DELETE_NOT_ALLOWED);

        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    void 판매완료된_게시글은_삭제할_수_있다() {
        UsedProduct product = product();
        product.markSold();
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        usedProductService.delete(SELLER_ID, PRODUCT_ID);

        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    void 남의_게시글은_삭제할_수_없다() {
        when(usedProductRepository.findByUsedProductIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> usedProductService.delete(OTHER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private UsedProductCreateRequestDto createRequest(UsedProductPriceType priceType, BigDecimal price) {
        UsedProductCreateRequestDto request = new UsedProductCreateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", CATEGORY_ID);
        ReflectionTestUtils.setField(request, "regionId", REGION_ID);
        ReflectionTestUtils.setField(request, "title", "자전거 팝니다");
        ReflectionTestUtils.setField(request, "content", "거의 새것입니다");
        ReflectionTestUtils.setField(request, "priceType", priceType);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }

    private UsedProductUpdateRequestDto updateRequest(UsedProductPriceType priceType, BigDecimal price) {
        UsedProductUpdateRequestDto request = new UsedProductUpdateRequestDto();
        ReflectionTestUtils.setField(request, "categoryId", CATEGORY_ID);
        ReflectionTestUtils.setField(request, "title", "수정된 제목");
        ReflectionTestUtils.setField(request, "content", "수정된 본문");
        ReflectionTestUtils.setField(request, "priceType", priceType);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }

    private UsedProduct product() {
        UsedProduct product = UsedProduct.create(
                seller(), usedCategory(), region(), "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }

    private Account seller() {
        Account account = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", SELLER_ID);
        return account;
    }

    private Category usedCategory() {
        Category category = Category.createRoot(CategoryType.USED, "디지털기기", 1);
        ReflectionTestUtils.setField(category, "categoryId", CATEGORY_ID);
        return category;
    }

    private Region region() {
        Region region = Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3);
        ReflectionTestUtils.setField(region, "regionId", REGION_ID);
        return region;
    }

    private AccountRegion accountRegion(boolean verified) {
        AccountRegion accountRegion = AccountRegion.create(seller(), region());
        ReflectionTestUtils.setField(accountRegion, "verified", verified);
        return accountRegion;
    }
}
