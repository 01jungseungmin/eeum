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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 삭제와 찜 쓰기가 동시에 들어올 때의 경쟁을 실제 MySQL에서 검증한다.
 * <p>
 * <b>등록 경쟁</b> — 잠금이 없으면 찜 트랜잭션의 스냅샷이 삭제 커밋 이전 상태를 보므로,
 * 이미 삭제된 글에 찜이 붙는다. 그 찜은 삭제 시 정리(deleteAllByRefTypeAndRefId)를
 * 이미 지나쳤으므로 영영 남는다.
 * <p>
 * <b>해제 경쟁</b> — 삭제 직전 재조회가 일반 SELECT면 REPEATABLE READ 스냅샷을 읽어
 * 그 사이 커밋된 삭제를 보지 못한다. 이미 사라진 행을 지우려다 flush에서
 * {@code Unexpected row count (expected 1 but was 0)}으로 끝난다 — 사용자에게는 500이다.
 * 트랜잭션이 통째로 롤백되므로 카운터 자체는 지켜지지만, 정상 요청이 서버 오류로 끝난다.
 * 잠금을 잡은 뒤의 current read여야 깨끗한 404가 된다.
 * <p>
 * 단위 테스트는 트랜잭션 스냅샷과 행 잠금을 재현하지 못해 이 경쟁들을 잡을 수 없다.
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
    private Long otherViewerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        Account viewer = accountRepository.save(Account.createUser(
                "viewer@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));
        // 카운터 오차를 눈에 보이게 하려면 남의 찜이 하나 남아 있어야 한다.
        // 혼자 찜한 상태면 두 번 깎여도 favoriteCount > 0 가드에 걸려 0에서 멈춘다.
        Account otherViewer = accountRepository.save(Account.createUser(
                "other@test.com", "encoded_pw", "다른사람", "다른사람닉", "010-3333-3333"));
        sellerId = seller.getAccountId();
        viewerId = viewer.getAccountId();
        otherViewerId = otherViewer.getAccountId();

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
            assertThat(awaitLockWait("used_product"))
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
        assertThat(favoritesOnProduct()).isZero();
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount()).isZero();
    }

    @Test
    void favoriteId로_찜_해제_중_게시글이_삭제되면_500이_아니라_찾을_수_없음으로_끝난다() throws Exception {
        // given: 두 사람이 찜한 상태 — 게시글 삭제는 둘 다 정리하고 카운터를 0으로 되돌린다.
        favoriteService.toggleFavorite(viewerId, toggleRequest());
        favoriteService.toggleFavorite(otherViewerId, toggleRequest());
        long favoriteId = viewerFavoriteId();

        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch unfavoriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicReference<Throwable> unfavoriteFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // T1: 게시글을 잠그고 삭제(찜 CASCADE 정리 포함)한 뒤 커밋 신호를 기다린다.
            Future<?> deletion = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        usedProductService.delete(sellerId, productId);
                        deleteLocked.countDown();
                        awaitQuietly(releaseDelete);
                    }));

            // T2: 삭제가 커밋되기 전에 자기 찜을 해제한다.
            Future<?> unfavorite = executor.submit(() -> {
                awaitQuietly(deleteLocked);
                unfavoriteStarted.countDown();
                try {
                    favoriteService.deleteFavorite(viewerId, favoriteId);
                } catch (Throwable e) {
                    unfavoriteFailure.set(e);
                }
            });

            awaitQuietly(unfavoriteStarted);
            assertThat(awaitLockWait("used_product"))
                    .as("찜 해제가 삭제 트랜잭션의 행 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음")
                    .isTrue();
            releaseDelete.countDown();

            deletion.get(30, TimeUnit.SECONDS);
            unfavorite.get(30, TimeUnit.SECONDS);
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }

        // then: 잠금 후 재조회가 일반 SELECT면 스냅샷이 이미 지워진 행을 보고 지우려다
        // flush에서 "expected row count 1 but was 0"으로 끝난다(500).
        // current read여야 사라진 것을 보고 깨끗한 404로 끝난다.
        assertThat(unfavoriteFailure.get())
                .as("이미 정리된 찜을 지우려다 영속성 예외로 터지면 안 된다")
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);

        assertThat(favoritesOnProduct()).isZero();
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount()).isZero();
    }

    @Test
    void favoriteId로_같은_찜을_중복_해제해도_500_없이_카운터가_유지된다() throws Exception {
        // given: 두 사람이 찜해 카운터는 2다. 한 사람이 같은 해제를 두 번 보낸다(재시도·두 기기).
        favoriteService.toggleFavorite(viewerId, toggleRequest());
        favoriteService.toggleFavorite(otherViewerId, toggleRequest());
        long favoriteId = viewerFavoriteId();

        CountDownLatch firstDeleted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // T1: 해제를 수행한 뒤 커밋하지 않고 계정 잠금을 쥐고 있는다.
            Future<?> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        favoriteService.deleteFavorite(viewerId, favoriteId);
                        firstDeleted.countDown();
                        awaitQuietly(releaseFirst);
                    }));

            // T2: 같은 사용자의 두 번째 해제 요청 — 계정 잠금에서 대기한다.
            Future<?> second = executor.submit(() -> {
                awaitQuietly(firstDeleted);
                secondStarted.countDown();
                try {
                    favoriteService.deleteFavorite(viewerId, favoriteId);
                } catch (Throwable e) {
                    secondFailure.set(e);
                }
            });

            awaitQuietly(secondStarted);
            assertThat(awaitLockWait("account"))
                    .as("두 번째 해제가 계정 행 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음")
                    .isTrue();
            releaseFirst.countDown();

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        // then: 두 번째 요청은 이미 사라진 행을 보고 멈춰야 한다.
        assertThat(secondFailure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);

        // 남의 찜 1건이 남아 있으므로 정답은 1이다. 스냅샷을 읽으면 두 번째 요청이 flush에서
        // 터져 롤백되므로 카운터는 결과적으로 지켜진다 — 이 단언은 회귀 검출용이 아니라
        // 정상 경로의 불변식 고정용이고, 회귀를 잡는 것은 위의 예외 타입 단언이다.
        assertThat(favoritesOnProduct()).isEqualTo(1);
        assertThat(usedProductRepository.findById(productId).orElseThrow().getFavoriteCount())
                .as("해제 1건은 정확히 1만 깎아야 한다")
                .isEqualTo(1);
    }

    // 공유 DB에서 전역 count()는 다른 클래스의 잔여 데이터에 걸려 거짓 실패를 낸다.
    // IntegrationTestSupport가 경고하는 지점이라, 이 게시글에 달린 찜만 센다.
    private long favoritesOnProduct() {
        return favoriteRepository.countByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, productId);
    }

    // favoriteId 경로를 쓰는 이유: 이 경로만 잠금 전에 일반 SELECT(findRefByFavoriteIdAndAccountId)로
    // 대상을 읽는다. MySQL REPEATABLE READ는 첫 비잠금 읽기에서 스냅샷을 만들므로, 그 지점이
    // 경쟁 트랜잭션의 커밋보다 앞선다. refType+refId 경로는 계정 잠금(FOR UPDATE)부터 시작해
    // 스냅샷이 잠금 획득 이후에 잡히고, 그래서 일반 SELECT로 바꿔도 이 경쟁이 재현되지 않는다.
    private long viewerFavoriteId() {
        return favoriteRepository
                .findByAccount_AccountIdAndRefTypeAndRefId(
                        viewerId, FavoriteRefType.USED_PRODUCT, productId)
                .orElseThrow()
                .getFavoriteId();
    }

    private FavoriteToggleRequestDto toggleRequest() {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", productId);
        return request;
    }
}
