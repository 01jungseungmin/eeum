package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductSearchRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 중고 목록 정렬 검증 — 실제 순서와 응답 Sort 메타데이터가 같은지 본다.
 *
 * <p>가격제안(NEGOTIABLE)은 {@code price}가 null이라 정렬에서 NULL이 섞인다. MySQL은
 * NULLS LAST 구문이 없어 Hibernate가 {@code case when price is null ...}로 에뮬레이션하므로,
 * 실제 실행 순서는 Mock으로 재현할 수 없다.
 *
 * <p>메타데이터를 함께 보는 이유: 응답 {@code Slice.pageable.sort}가 NATIVE라고 말하면
 * 클라이언트는 MySQL 기준으로 "ASC일 때 NULL이 앞"이라 읽는다. 실제와 정반대다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductSortIntegrationTest extends IntegrationTestSupport {

    private final UsedProductService usedProductService;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Region region;
    private Category category;
    private Long regionId;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));
        regionId = region.getRegionId();

        // 가격이 있는 글 사이에 가격제안(price = null)을 섞는다.
        fixedPrice("저가", new BigDecimal("1000"));
        negotiable("가격제안");
        fixedPrice("고가", new BigDecimal("30000"));
        fixedPrice("중가", new BigDecimal("10000"));
    }

    @AfterEach
    void tearDown() {
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 가격_오름차순에서_가격제안_글은_맨_뒤로_간다() {
        CursorSlice<UsedProductSummaryResponseDto> result = search(Sort.by(Sort.Direction.ASC, "price"));

        assertThat(result.getContent())
                .extracting(UsedProductSummaryResponseDto::getTitle)
                .containsExactly("저가", "중가", "고가", "가격제안");
    }

    @Test
    void 가격_내림차순에서도_가격제안_글은_맨_뒤로_간다() {
        // NULLS LAST는 방향과 무관하게 유지돼야 한다. 방향만 뒤집으면 가격제안이 맨 앞으로 온다.
        CursorSlice<UsedProductSummaryResponseDto> result = search(Sort.by(Sort.Direction.DESC, "price"));

        assertThat(result.getContent())
                .extracting(UsedProductSummaryResponseDto::getTitle)
                .containsExactly("고가", "중가", "저가", "가격제안");
    }

    @Test
    void 응답_Sort_메타데이터의_NULL_처리가_실제_실행과_일치한다() {
        CursorSlice<UsedProductSummaryResponseDto> result = search(Sort.by(Sort.Direction.ASC, "price"));

        assertThat(result.getSort())
                .as("SQL은 NULLS LAST인데 메타데이터가 그 말을 빠뜨리면 클라이언트는 정반대로 읽는다")
                .contains("price,ASC,NULLS_LAST");
    }

    @Test
    void NOT_NULL_필드는_NULL_처리를_주장하지_않는다() {
        // createdAt·tie-break에까지 NULLS LAST를 붙이면 SQL에 불필요한 case 식이 생기고
        // 메타데이터도 실제로 일어나지 않는 NULL 처리를 주장하게 된다.
        CursorSlice<UsedProductSummaryResponseDto> result = search(Sort.by(Sort.Direction.DESC, "createdAt"));

        // NULL 처리를 붙이지 않은 항목은 "속성,방향"까지만 나온다.
        assertThat(result.getSort())
                .containsExactly("createdAt,DESC", "usedProductId,DESC");
    }

    @Test
    void 가격_정렬에도_tie_break가_메타데이터에_포함된다() {
        // SQL에만 붙이고 메타데이터에서 빠뜨리면 응답이 실제 정렬을 설명하지 못한다.
        CursorSlice<UsedProductSummaryResponseDto> result = search(Sort.by(Sort.Direction.ASC, "price"));

        assertThat(result.getSort()).contains("usedProductId,DESC");
    }

    private CursorSlice<UsedProductSummaryResponseDto> search(Sort sort) {
        return usedProductService.getRegionProducts(
                null,
                new UsedProductSearchRequestDto(regionId, null, null, null, null, null, null),
                null,
                null,
                PageRequest.of(0, 20, sort));
    }

    private void fixedPrice(String title, BigDecimal price) {
        save(title, UsedProductPriceType.FIXED, price);
    }

    private void negotiable(String title) {
        save(title, UsedProductPriceType.NEGOTIABLE, null);
    }

    private void save(String title, UsedProductPriceType priceType, BigDecimal price) {
        Account seller = accountRepository.save(Account.createUser(
                title + "@test.com", "encoded_pw", "판매자", "닉" + title, "010-2222-2222"));
        usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, title, "설명", priceType, price));
    }
}
