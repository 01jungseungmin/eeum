package com.eeum.eeum.application.favorite.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 삭제와 찜 등록이 동시에 들어올 때의 경쟁을 실제 MySQL에서 검증한다.
 * <p>
 * 잠금이 없으면 찜 트랜잭션의 스냅샷이 삭제 커밋 이전 상태를 보므로, 이미 삭제된 글에 찜이 붙는다.
 * 그 찜은 삭제 시 정리(deleteAllByRefTypeAndRefId)를 이미 지나쳤으므로 영영 남는다.
 * 단위 테스트는 트랜잭션 스냅샷과 행 잠금을 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductFavoriteConcurrencyIntegrationTest extends IntegrationTestSupport {


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

    @Test
    void 삭제_커밋_전에_들어온_찜은_죽은_찜으로_남지_않는다() throws Exception {
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch favoriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicReference<Throwable> favoriteFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // T1: 게시글을 잠그고 삭제한 뒤, 커밋 신호를 받을 때까지 잠금을 쥐고 있는다.
            Future<?> deletion = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        usedProductService.delete(sellerId, productId);
                        deleteLocked.countDown();
                        awaitQuietly(releaseDelete);
                    }));

            // T2: 삭제가 아직 커밋되지 않은 상태에서 찜을 시도한다.
            Future<?> favorite = executor.submit(() -> {
                awaitQuietly(deleteLocked);
                favoriteStarted.countDown();
                try {
                    favoriteService.toggleFavorite(viewerId, toggleRequest());
                } catch (Throwable e) {
                    favoriteFailure.set(e);
                }
            });

            // 경과 시간으로 판정하면 스레드 스케줄링이 밀렸을 때 잠금 없이도 통과한다.
            // MySQL이 실제로 "이 트랜잭션은 잠금 대기 중"이라고 보고할 때까지 기다린 뒤 커밋시킨다.
            awaitQuietly(favoriteStarted);
            assertThat(awaitLockWait())
                    .as("찜 요청이 삭제 트랜잭션의 행 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음")
                    .isTrue();
            releaseDelete.countDown();

            deletion.get(30, TimeUnit.SECONDS);
            favorite.get(30, TimeUnit.SECONDS);
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }

        // then 1: 찜은 삭제된 글을 대상으로 성립하지 않는다.
        assertThat(favoriteFailure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        // then 2: 잠금이 없으면 여기서 죽은 찜 1건이 남는다.
        assertThat(favoriteRepository.count()).isZero();
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount()).isZero();
    }

    // 찜 트랜잭션이 행 잠금 대기에 들어갔는지 MySQL에 직접 물어본다.
    // 잠금 대기 "개수"만 세면 이 테스트와 무관한 트랜잭션(다른 테스트·스케줄러)이 잡혀
    // 경쟁이 재현되지 않았는데도 통과할 수 있다. 대기 중인 잠금의 대상 테이블까지 확인한다.
    // performance_schema 조회는 권한이 필요해 컨테이너 root 계정으로 별도 접속한다.
    private boolean awaitLockWait() throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        try (Connection connection = DriverManager.getConnection(
                mysqlJdbcUrl(), "root", mysqlPassword())) {
            while (System.currentTimeMillis() < deadline) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery(
                             """
                             SELECT COUNT(*)
                             FROM performance_schema.data_lock_waits w
                             JOIN performance_schema.data_locks l
                               ON w.REQUESTING_ENGINE_LOCK_ID = l.ENGINE_LOCK_ID
                             WHERE l.OBJECT_NAME = 'used_product'
                             """)) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return true;
                    }
                }
                sleepQuietly(100);
            }
        }
        return false;
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
