package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.dto.response.FavoriteUsedProductResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 중고 게시글 찜 목록의 노출 필터가 실제 MySQL 쿼리에서 페이징 전에 적용되는지 검증한다.
 * Mock 단위 테스트는 리포지토리 반환값을 그대로 받으므로 "필터가 페이징 전인지"를 구조적으로 확인할 수 없다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class FavoriteUsedProductListIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // 목록 조립 중 LAZY 초기화가 없는지 실제 실행 쿼리 수로 확인하기 위해 통계를 켠다.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private final FavoriteService favoriteService;
    private final FavoriteRepository favoriteRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManagerFactory entityManagerFactory;

    private Long viewerId;
    private final List<Long> productIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Account viewer = accountRepository.save(Account.createUser(
                "viewer@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        viewerId = viewer.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));

        // 6건을 등록하고 전부 찜한다 — 찜한 뒤 숨김/삭제되는 실제 순서를 그대로 재현한다.
        for (int i = 0; i < 6; i++) {
            UsedProduct product = usedProductRepository.saveAndFlush(UsedProduct.create(
                    seller, category, region,
                    "자전거 팝니다 " + i, "거의 새것입니다",
                    UsedProductPriceType.FIXED, new BigDecimal("10000")));
            productIds.add(product.getUsedProductId());
            favoriteService.toggleFavorite(viewerId, toggleRequest(product.getUsedProductId()));
        }
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
        productIds.clear();
    }

    @Test
    void 숨김_삭제_게시글은_페이징_전에_걸러져_요청한_페이지_크기가_유지된다() {
        // given: 6건 중 3건을 숨김·삭제 처리해 노출 대상은 3건만 남긴다.
        hide(productIds.get(0));
        hide(productIds.get(1));
        softDelete(productIds.get(2));

        // when: 조회 후 메모리에서 걸렀다면 첫 페이지는 0건, 두 번째 페이지는 1건이 된다.
        Slice<FavoriteUsedProductResponseDto> first =
                favoriteService.getMyFavoriteUsedProducts(viewerId, PageRequest.of(0, 2));
        Slice<FavoriteUsedProductResponseDto> second =
                favoriteService.getMyFavoriteUsedProducts(viewerId, PageRequest.of(1, 2));

        // then: 노출 대상 3건이 요청한 size 2 기준으로 2건 + 1건으로 정확히 나뉜다.
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.hasNext()).isFalse();

        List<Long> shown = new ArrayList<>(first.getContent().stream()
                .map(FavoriteUsedProductResponseDto::getUsedProductId).toList());
        shown.addAll(second.getContent().stream()
                .map(FavoriteUsedProductResponseDto::getUsedProductId).toList());
        assertThat(shown).containsExactlyInAnyOrder(
                productIds.get(3), productIds.get(4), productIds.get(5));
    }

    @Test
    void 찜_목록은_항목_수와_무관하게_고정된_쿼리만_실행한다() {
        // given: region은 LAZY 연관이라 DTO에서 접근하면 항목 수만큼 SELECT가 추가된다(N+1).
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(viewerId, PageRequest.of(0, 6));

        // then: 목록 조회 1번 + 대표 사진 배치 조회 1번으로 고정된다.
        assertThat(result.getContent()).hasSize(6);
        assertThat(result.getContent()).allSatisfy(
                dto -> assertThat(dto.getRegionName()).isEqualTo("역삼동"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void 숨김_게시글은_ID를_직접_지정해도_찜할_수_없다() {
        // given: 목록을 거치지 않고 ID만으로 찜하면 favoriteCount가 오염된다.
        Long hiddenId = productIds.get(0);
        favoriteService.deleteFavoriteByRef(viewerId, FavoriteRefType.USED_PRODUCT, hiddenId);
        hide(hiddenId);
        int countBefore = usedProductRepository.findById(hiddenId).orElseThrow().getFavoriteCount();

        // when & then
        assertThatThrownBy(() -> favoriteService.toggleFavorite(viewerId, toggleRequest(hiddenId)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        assertThat(usedProductRepository.findById(hiddenId).orElseThrow().getFavoriteCount())
                .isEqualTo(countBefore);
    }

    private void hide(Long productId) {
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.hide();
        usedProductRepository.saveAndFlush(product);
    }

    private void softDelete(Long productId) {
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.softDelete();
        usedProductRepository.saveAndFlush(product);
    }

    private FavoriteToggleRequestDto toggleRequest(Long refId) {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", refId);
        return request;
    }
}
