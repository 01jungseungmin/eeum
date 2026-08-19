package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.used.service.UsedProductService;
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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 삭제와 찜 등록이 동시에 들어올 때의 경쟁을 실제 MySQL에서 검증한다.
 * <p>
 * 잠금이 없으면 찜 트랜잭션의 스냅샷이 삭제 커밋 이전 상태를 보므로, 이미 삭제된 글에 찜이 붙는다.
 * 그 찜은 삭제 시 정리(deleteAllByRefTypeAndRefId)를 이미 지나쳤으므로 영영 남는다.
 * 단위 테스트는 트랜잭션 스냅샷과 행 잠금을 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class UsedProductFavoriteConcurrencyIntegrationTest {

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
    }

    private final FavoriteService favoriteService;
    private final UsedProductService usedProductService;
    private final FavoriteRepository favoriteRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

    private Long sellerId;
    private Long viewerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        Account viewer = accountRepository.save(Account.createUser(
                "viewer@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));
        sellerId = seller.getAccountId();
        viewerId = viewer.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region,
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // T1이 잠금을 쥐고 버티는 시간. T2가 이만큼 대기했는지로 "실제로 잠금에 걸렸는지"를 판정한다.
    private static final long LOCK_HOLD_MILLIS = 1_000L;

    @Test
    void 삭제_커밋_전에_들어온_찜은_죽은_찜으로_남지_않는다() throws Exception {
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch favoriteStarted = new CountDownLatch(1);
        AtomicReference<Throwable> favoriteFailure = new AtomicReference<>();
        AtomicLong favoriteElapsedMillis = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // T1: 게시글을 잠그고 삭제한 뒤, 찜 요청이 들어온 것을 확인하고도 잠시 커밋을 미룬다.
            Future<?> deletion = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        usedProductService.delete(sellerId, productId);
                        deleteLocked.countDown();
                        awaitQuietly(favoriteStarted);
                        sleepQuietly(LOCK_HOLD_MILLIS);
                    }));

            // T2: 삭제가 아직 커밋되지 않은 상태에서 찜을 시도한다.
            Future<?> favorite = executor.submit(() -> {
                awaitQuietly(deleteLocked);
                favoriteStarted.countDown();
                long startedAt = System.nanoTime();
                try {
                    favoriteService.toggleFavorite(viewerId, toggleRequest());
                } catch (Throwable e) {
                    favoriteFailure.set(e);
                } finally {
                    favoriteElapsedMillis.set((System.nanoTime() - startedAt) / 1_000_000);
                }
            });

            deletion.get(30, TimeUnit.SECONDS);
            favorite.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // then 1: 찜 요청이 실제로 잠금 대기에 걸렸는지 확인한다.
        // 이 단언이 없으면, 느린 환경에서 삭제가 먼저 커밋돼 버려 잠금이 없어도 통과하는
        // 무의미한 테스트가 된다(경쟁을 재현하지 못한 채 결과만 맞는 경우).
        assertThat(favoriteElapsedMillis.get())
                .as("찜 요청이 삭제 트랜잭션의 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음")
                .isGreaterThanOrEqualTo(LOCK_HOLD_MILLIS / 2);

        // then 2: 찜은 삭제된 글을 대상으로 성립하지 않는다.
        assertThat(favoriteFailure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        // then 3: 잠금이 없으면 여기서 죽은 찜 1건이 남는다.
        assertThat(favoriteRepository.count()).isZero();
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount()).isZero();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private FavoriteToggleRequestDto toggleRequest() {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", productId);
        return request;
    }
}
