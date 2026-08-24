package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadListRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 사진 쓰기가 동시에 들어올 때의 경쟁을 실제 MySQL에서 검증한다.
 *
 * <p><b>이 테스트가 필요한 이유</b> — {@code used_product_image}에는 "게시글당 대표 사진 1장"을
 * 강제하는 DB 제약이 없다. 상한(10장)·표시 순서·대표 사진 유일성이 전부 애플리케이션 잠금
 * ({@code UsedProductImageService.getOwnedOrThrow}의 account → used_product 잠금)에만 걸려 있다.
 * 운영 runbook이 {@code HAVING thumbnail_count <> 1}로 사후 점검하는 항목이기도 하다.
 *
 * <p>동시 요청이 각자 "현재 사진 수"·"현재 대표"를 읽고 진행하면 10장 초과, 순서 중복,
 * 대표 복수·부재가 생긴다. 부모 게시글 행 잠금이 이 경쟁을 직렬화한다.
 * 단위 테스트는 행 잠금과 트랜잭션 스냅샷을 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductImageConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final UsedProductImageService usedProductImageService;
    private final UsedProductImageRepository usedProductImageRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

    private Long sellerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        // 픽스처 식별자를 고정하면, 앞선 테스트가 실패해 정리를 마치지 못했을 때 다음 테스트가
        // Duplicate entry로 터진다. 진짜 원인이 중복 키 오류에 가려지므로 매번 다른 값을 쓴다.
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account seller = accountRepository.save(Account.createUser(
                "image-seller-" + tag + "@test.com", "encoded_pw", "판매자", "사진판매자" + tag, "010-2222-2222"));
        sellerId = seller.getAccountId();

        Region region = regionRepository.save(
                Region.create("11680101" + tag.substring(0, 2), "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region,
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();
    }

    @AfterEach
    void tearDown() {
        usedProductImageRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 동시_사진_추가는_10장_상한을_넘기지_않는다() throws Exception {
        // given: 6장씩 두 번. 각자 "현재 0장"을 읽고 진행하면 12장이 된다.
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        race(
                () -> usedProductImageService.addImages(sellerId, productId, upload(6, "first")),
                () -> usedProductImageService.addImages(sellerId, productId, upload(6, "second")),
                secondFailure);

        // then: 뒤에 온 요청은 잠금 해제 후 실제 6장을 보고 상한에 걸린다.
        assertThat(secondFailure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED);

        assertThat(images()).hasSize(6);
    }

    @Test
    void 동시_사진_추가로_대표_사진이_둘이_되지_않는다() throws Exception {
        // given: 빈 게시글에 동시에 첫 장을 올린다.
        // 첫 장 판정이 currentCount == 0이라, 둘 다 0을 읽으면 대표가 두 장이 된다.
        race(
                () -> usedProductImageService.addImages(sellerId, productId, upload(1, "first")),
                () -> usedProductImageService.addImages(sellerId, productId, upload(1, "second")),
                new AtomicReference<>());

        List<UsedProductImage> images = images();
        assertThat(images).hasSize(2);
        assertThat(thumbnailCount(images))
                .as("대표 사진은 정확히 한 장이어야 한다 — DB 제약이 없어 잠금이 유일한 방어다")
                .isEqualTo(1);

        // 표시 순서도 겹치면 안 된다. 둘 다 0을 읽으면 1번이 두 개 생긴다.
        assertThat(images).extracting(UsedProductImage::getDisplayOrder)
                .containsExactly(1, 2);
    }

    @Test
    void 대표_사진_삭제와_대표_변경이_겹쳐도_대표는_정확히_한_장이다() throws Exception {
        // given: A(대표) B C. 삭제는 A를 지우고 남은 첫 장을 대표로 승격시키고,
        // 변경은 C를 대표로 만든다. 직렬화되지 않으면 B와 C가 동시에 대표가 된다.
        usedProductImageService.addImages(sellerId, productId, upload(3, "img"));
        List<UsedProductImage> initial = images();
        Long thumbnailId = initial.get(0).getImageId();
        Long lastId = initial.get(2).getImageId();
        assertThat(initial.get(0).isThumbnail()).isTrue();

        race(
                () -> usedProductImageService.deleteImage(sellerId, productId, thumbnailId),
                () -> usedProductImageService.changeThumbnail(sellerId, productId, lastId),
                new AtomicReference<>());

        // then: 대표는 한 장이고, 나중에 커밋된 변경 요청의 대상이 대표다.
        List<UsedProductImage> remaining = images();
        assertThat(remaining).hasSize(2);
        assertThat(thumbnailCount(remaining))
                .as("삭제의 대표 승격과 변경이 겹쳐 대표가 둘이 되면 안 된다")
                .isEqualTo(1);
        assertThat(remaining.stream().filter(UsedProductImage::isThumbnail).findFirst().orElseThrow().getImageId())
                .isEqualTo(lastId);
    }

    /**
     * 두 쓰기를 같은 게시글에서 경쟁시킨다.
     *
     * <p>T1은 작업 후 커밋하지 않고 잠금을 쥐고 있고, T2는 그 사이에 같은 경로로 진입한다.
     * MySQL이 실제로 잠금 대기를 보고할 때까지 기다린 뒤 T1을 커밋시킨다 —
     * 경과 시간으로 판정하면 스케줄링이 밀렸을 때 잠금 없이도 통과한다.
     *
     * <p>대기 지점이 {@code account}인 것은 두 요청의 판매자가 같기 때문이다.
     * 잠금 순서 account → used_product의 첫 단계에서 이미 막힌다.
     */
    private void race(Runnable first, Runnable second, AtomicReference<Throwable> secondFailure)
            throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> t1 = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        first.run();
                        firstDone.countDown();
                        awaitQuietly(releaseFirst);
                    }));

            Future<?> t2 = executor.submit(() -> {
                awaitQuietly(firstDone);
                secondStarted.countDown();
                try {
                    second.run();
                } catch (Throwable e) {
                    secondFailure.set(e);
                }
            });

            awaitQuietly(secondStarted);
            assertThat(awaitLockWait("account"))
                    .as("두 번째 요청이 행 잠금을 기다리지 않았다 — 경쟁이 재현되지 않음")
                    .isTrue();
            releaseFirst.countDown();

            t1.get(30, TimeUnit.SECONDS);
            t2.get(30, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private List<UsedProductImage> images() {
        return usedProductImageRepository
                .findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(productId);
    }

    private long thumbnailCount(List<UsedProductImage> images) {
        return images.stream().filter(UsedProductImage::isThumbnail).count();
    }

    private UsedProductImageUploadListRequestDto upload(int count, String prefix) {
        UsedProductImageUploadListRequestDto request = new UsedProductImageUploadListRequestDto();
        List<UsedProductImageUploadRequestDto> images = Arrays.stream(new int[count].clone())
                .boxed()
                .map(ignored -> new UsedProductImageUploadRequestDto())
                .toList();
        for (int i = 0; i < images.size(); i++) {
            ReflectionTestUtils.setField(images.get(i), "imageUrl", prefix + "-" + i + ".jpg");
        }
        ReflectionTestUtils.setField(request, "images", images);
        return request;
    }
}
