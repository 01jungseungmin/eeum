package com.eeum.eeum.application.used.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동네 중고 게시글 공개 목록의 노출 조건을 실제 MySQL에서 검증한다.
 * <p>
 * 삭제·숨김·판매자 탈퇴는 QueryDSL 조인과 where로 걸리므로 Mock 단위 테스트가 재현하지 못한다.
 * 지역·카테고리 fetch join과 seller inner join이 섞인 상태에서 페이징이 정상인지도 함께 본다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductPublicListIntegrationTest extends IntegrationTestSupport {


    private final UsedProductService usedProductService;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long regionId;
    private Region region;
    private Category category;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));
        regionId = region.getRegionId();
    }

    @AfterEach
    void tearDown() {
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    void 삭제_숨김_판매자탈퇴_게시글은_목록에서_빠진다() {
        // given
        createProduct("정상1", seller("ok1"), product -> { });
        createProduct("정상2", seller("ok2"), product -> { });
        createProduct("숨김", seller("hidden"), UsedProduct::hide);
        createProduct("삭제", seller("deleted"), UsedProduct::softDelete);

        Account withdrawn = seller("withdrawn");
        createProduct("탈퇴판매자", withdrawn, product -> { });
        withdrawn.withdraw();
        accountRepository.saveAndFlush(withdrawn);

        // when
        Slice<UsedProductSummaryResponseDto> result = usedProductService.getRegionProducts(
                null, searchRequest(), null, null, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent())
                .extracting(UsedProductSummaryResponseDto::getTitle)
                .containsExactlyInAnyOrder("정상1", "정상2");
    }

    @Test
    void 판매자_조인을_추가해도_페이징이_정상_동작한다() {
        // given: fetch join(region·category)과 seller inner join이 섞인 상태에서
        // limit이 의도대로 걸리는지 확인한다.
        for (int i = 0; i < 5; i++) {
            createProduct("상품" + i, seller("s" + i), product -> { });
        }

        // when: 커서를 이어 붙여 끝까지 읽는다
        Slice<UsedProductSummaryResponseDto> first = usedProductService.getRegionProducts(
                null, searchRequest(), null, null, PageRequest.of(0, 2));
        Slice<UsedProductSummaryResponseDto> second = usedProductService.getRegionProducts(
                null, searchRequest(), cursorValue(first), cursorId(first), PageRequest.of(0, 2));
        Slice<UsedProductSummaryResponseDto> last = usedProductService.getRegionProducts(
                null, searchRequest(), cursorValue(second), cursorId(second), PageRequest.of(0, 2));

        // then
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(2);
        assertThat(second.hasNext()).isTrue();
        assertThat(last.getContent()).hasSize(1);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void 스크롤_도중_새_글이_등록돼도_경계가_중복되지_않는다() {
        // OFFSET 페이징이었을 때의 결함을 고정한다. 최신순 목록은 새 글이 맨 앞에 꽂히므로
        // 1페이지를 읽고 2페이지를 요청하는 사이 한 건이 등록되면 목록 전체가 한 칸 밀려,
        // 경계에 있던 글이 2페이지에서 그대로 다시 나왔다.
        for (int i = 0; i < 3; i++) {
            createProduct("상품" + i, seller("s" + i), product -> { });
        }

        Slice<UsedProductSummaryResponseDto> first = usedProductService.getRegionProducts(
                null, searchRequest(), null, null, PageRequest.of(0, 2));
        assertThat(first.getContent())
                .extracting(UsedProductSummaryResponseDto::getTitle)
                .containsExactly("상품2", "상품1");

        // when: 두 페이지를 읽는 사이 새 글이 등록된다
        createProduct("끼어든 상품", seller("late"), product -> { });

        Slice<UsedProductSummaryResponseDto> second = usedProductService.getRegionProducts(
                null, searchRequest(), cursorValue(first), cursorId(first), PageRequest.of(0, 2));

        // then: OFFSET(2)이었다면 여기서 "상품1"이 한 번 더 나온다
        assertThat(second.getContent())
                .extracting(UsedProductSummaryResponseDto::getTitle)
                .containsExactly("상품0");
    }

    // 직전 페이지의 마지막 항목 = 다음 요청의 커서. 기본 정렬(createdAt desc) 기준이라
    // 정렬 키 값은 createdAt이다. 클라이언트도 응답 필드만으로 같은 값을 만들 수 있다.
    private String cursorValue(Slice<UsedProductSummaryResponseDto> page) {
        return lastOf(page).getCreatedAt().toString();
    }

    private Long cursorId(Slice<UsedProductSummaryResponseDto> page) {
        return lastOf(page).getUsedProductId();
    }

    private UsedProductSummaryResponseDto lastOf(Slice<UsedProductSummaryResponseDto> page) {
        return page.getContent().get(page.getContent().size() - 1);
    }

    @Test
    void 목록_항목은_지역명과_카테고리명을_함께_담는다() {
        // fetch join이 seller 조인 추가로 깨지지 않았는지 확인한다
        createProduct("정상", seller("ok"), product -> { });

        Slice<UsedProductSummaryResponseDto> result = usedProductService.getRegionProducts(
                null, searchRequest(), null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getRegionName()).isEqualTo("역삼동");
                    assertThat(dto.getCategoryName()).isEqualTo("디지털기기");
                });
    }

    private UsedProductSearchRequestDto searchRequest() {
        return new UsedProductSearchRequestDto(regionId, null, null, null, null, null, null);
    }

    private Account seller(String name) {
        return accountRepository.save(Account.createUser(
                name + "@test.com", "encoded_pw", "판매자", "닉" + name, "010-2222-2222"));
    }

    private void createProduct(String title, Account seller, java.util.function.Consumer<UsedProduct> mutator) {
        UsedProduct product = UsedProduct.create(
                seller, category, region, title, "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        mutator.accept(product);
        usedProductRepository.saveAndFlush(product);
    }
}
