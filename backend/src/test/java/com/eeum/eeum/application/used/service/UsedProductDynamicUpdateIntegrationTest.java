package com.eeum.eeum.application.used.service;

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
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UsedProduct의 부분 UPDATE(@DynamicUpdate) 회귀 테스트.
 * <p>
 * favoriteCount·viewCount는 엔티티 dirty checking이 아니라 별도 원자 UPDATE로 증감된다.
 * 전체 컬럼을 쓰는 UPDATE가 나가면, 그 값을 읽어둔 시점 이후의 증감이 오래된 값으로 덮어써져 조용히 사라진다.
 * 단위 테스트는 실제 UPDATE 문을 만들지 않으므로 이 회귀를 잡지 못한다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class UsedProductDynamicUpdateIntegrationTest {

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
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> SqlCaptureInspector.class.getName());
    }

    private final AdminUsedProductService adminUsedProductService;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

    private Long productId;

    @BeforeEach
    void setUp() {
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));

        UsedProduct product = UsedProduct.create(
                seller, category, region,
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        product.hide();
        productId = usedProductRepository.saveAndFlush(product).getUsedProductId();
    }

    @AfterEach
    void tearDown() {
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 게시글_상태_변경이_그_사이_증가한_찜_수를_덮어쓰지_않는다() {
        // given: 숨김 해제 트랜잭션이 게시글을 읽은 뒤, 커밋 전에 다른 트랜잭션에서 찜이 3건 늘어난다.
        SqlCaptureInspector.reset();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UsedProduct loaded = usedProductRepository.findById(productId).orElseThrow();
            assertThat(loaded.getFavoriteCount()).isZero();

            incrementFavoriteCountInOtherTransaction(3);

            loaded.show();   // 커밋 시 dirty checking UPDATE
        });

        // then 1: 전체 컬럼을 쓰는 UPDATE라면 favoriteCount가 읽어둔 0으로 되돌아간다.
        UsedProduct reloaded = usedProductRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getFavoriteCount()).isEqualTo(3);
        assertThat(reloaded.isHidden()).isFalse();

        // then 2: 상태 변경 UPDATE에는 바꾸지 않은 카운터 컬럼이 아예 포함되지 않는다.
        List<String> updates = SqlCaptureInspector.captured().stream()
                .filter(sql -> sql.toLowerCase().startsWith("update used_product"))
                .toList();
        assertThat(updates).as("실행 UPDATE: %s", updates).hasSize(1);
        assertThat(updates.get(0)).contains("is_hidden").doesNotContain("favorite_count", "view_count");
    }

    @Test
    void 관리자_숨김_해제도_같은_방식으로_찜_수를_보존한다() {
        // given: 서비스 경로(잠금 조회 포함)로도 같은지 확인한다.
        incrementFavoriteCountInOtherTransaction(2);

        // when
        adminUsedProductService.show(productId);

        // then
        UsedProduct reloaded = usedProductRepository.findById(productId).orElseThrow();
        assertThat(reloaded.isHidden()).isFalse();
        assertThat(reloaded.getFavoriteCount()).isEqualTo(2);
    }

    // 별도 스레드 + 별도 트랜잭션으로 찜 카운트를 올린다.
    // 같은 스레드의 REQUIRES_NEW로는 @Modifying(clearAutomatically)이 바깥 영속성 컨텍스트까지 건드려
    // 검증하려는 상황(엔티티를 읽어둔 채로 DB만 바뀐 상태)이 만들어지지 않는다.
    private void incrementFavoriteCountInOtherTransaction(int times) {
        CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    for (int i = 0; i < times; i++) {
                        usedProductRepository.incrementFavoriteCount(productId);
                    }
                })).join();
    }
}
