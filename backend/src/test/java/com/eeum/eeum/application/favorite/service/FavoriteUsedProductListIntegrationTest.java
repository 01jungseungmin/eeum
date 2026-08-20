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
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
 * 중고 게시글 찜의 DB 의존 동작을 실제 MySQL에서 검증한다.
 * 노출 필터가 페이징 전에 적용되는지, 탈퇴 정리가 IN UPDATE 한 번으로 끝나는지,
 * 중복 찜을 UNIQUE 제약이 막는지는 Mock 단위 테스트로 확인할 수 없다.
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
        // 목록 조립 중 LAZY 초기화가 없는지 실제 실행 SQL로 확인한다.
        // 전역 통계는 같은 컨텍스트의 스케줄러가 배경에서 날리는 쿼리까지 세므로 스레드별 수집기를 쓴다.
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> SqlCaptureInspector.class.getName());
    }

    private final FavoriteService favoriteService;
    private final AdminFavoriteService adminFavoriteService;
    private final FavoriteRepository favoriteRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

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
        SqlCaptureInspector.reset();

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(viewerId, PageRequest.of(0, 6));
        List<String> executedSql = SqlCaptureInspector.captured();

        // then: 목록 조회 1번 + 대표 사진 배치 조회 1번으로 고정된다.
        assertThat(result.getContent()).hasSize(6);
        assertThat(result.getContent()).allSatisfy(
                dto -> assertThat(dto.getRegionName()).isEqualTo("역삼동"));
        assertThat(executedSql).as("실행 SQL: %s", executedSql).hasSize(2);
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

    @Test
    void 회원_탈퇴_시_찜한_게시글의_카운트가_IN_UPDATE_한_번으로_모두_감소한다() {
        // given: 6건 모두 찜한 상태라 favoriteCount는 각각 1이다.
        assertThat(usedProductRepository.findAllById(productIds))
                .allSatisfy(product -> assertThat(product.getFavoriteCount()).isEqualTo(1));
        SqlCaptureInspector.reset();

        // when
        favoriteService.deleteAllByAccountId(viewerId);

        // then: 찜 수만큼 UPDATE를 날리지 않고 IN 절 UPDATE 한 번으로 6건이 함께 감소한다.
        assertThat(usedProductRepository.findAllById(productIds))
                .allSatisfy(product -> assertThat(product.getFavoriteCount()).isZero());
        assertThat(favoriteRepository.count()).isZero();

        List<String> updates = SqlCaptureInspector.captured().stream()
                .filter(sql -> sql.toLowerCase().startsWith("update used_product"))
                .toList();
        assertThat(updates).as("실행 UPDATE: %s", updates).hasSize(1);
    }

    @Test
    void 같은_대상을_두_번_찜하면_UNIQUE_제약이_막는다() {
        // given: 서비스는 이 제약을 전제로 동시 중복 요청을 FAVORITE_ALREADY_EXISTS로 처리한다.
        // 제약이 없으면 중복 행이 쌓여 favoriteCount가 부풀고, 단건 찜 조회(Optional)가 터진다.
        Account viewer = accountRepository.findById(viewerId).orElseThrow();
        Long alreadyFavorited = productIds.get(0);

        // when & then
        assertThatThrownBy(() -> favoriteRepository.saveAndFlush(
                Favorite.create(viewer, FavoriteRefType.USED_PRODUCT, alreadyFavorited)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 어긋난_찜_수는_재계산으로_실제_찜_행_수에_맞춰진다() {
        // given: 중복 찜 정리나 장애 복구 후처럼 카운트만 부풀어 있는 상태를 만든다.
        Long productId = productIds.get(0);
        // @Modifying 쿼리라 트랜잭션 안에서 실행해야 한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            usedProductRepository.incrementFavoriteCount(productId);
            usedProductRepository.incrementFavoriteCount(productId);
        });
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount())
                .isEqualTo(3);   // 실제 찜 행은 1건

        // when
        adminFavoriteService.recalculateFavoriteCounts(FavoriteRefType.USED_PRODUCT);

        // then: 모든 게시글이 favorite 테이블 실제 행 수로 맞춰진다.
        assertThat(usedProductRepository.findAllById(productIds))
                .allSatisfy(product -> assertThat(product.getFavoriteCount()).isEqualTo(1));
    }

    @Test
    void 판매자가_탈퇴한_게시글은_찜_목록에서도_빠진다() {
        // given: 판매자 탈퇴는 글을 내리는 사유다. 목록·상세에서 사라진 글이
        // 찜 목록에만 남아 있으면 탈퇴자에게 거래 문의가 계속 간다.
        Account seller = accountRepository.findAll().stream()
                .filter(account -> !account.getAccountId().equals(viewerId))
                .findFirst()
                .orElseThrow();
        seller.withdraw();
        accountRepository.saveAndFlush(seller);

        // when
        Slice<FavoriteUsedProductResponseDto> result =
                favoriteService.getMyFavoriteUsedProducts(viewerId, PageRequest.of(0, 20));

        // then: 판매자가 한 명이므로 전부 빠진다
        assertThat(result.getContent()).isEmpty();
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
