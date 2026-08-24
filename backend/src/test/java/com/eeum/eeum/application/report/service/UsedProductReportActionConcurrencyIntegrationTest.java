package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.application.used.service.AdminUsedProductService;
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
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신고 조치(숨김·삭제)와 같은 게시글에 대한 다른 쓰기의 경쟁을 실제 MySQL에서 검증한다.
 *
 * <p><b>무엇이 잠금으로 지켜지는가</b> — {@code UsedProduct}는 {@code @DynamicUpdate}라
 * 바뀐 컬럼만 UPDATE한다. 그래서 "판매자가 제목을 고치는 동안 관리자가 숨긴다"처럼 서로 다른
 * 컬럼을 건드리는 조합은 잠금이 없어도 덮어쓰지 않는다. 잠금이 실제로 지키는 것은 두 가지다.
 *
 * <ul>
 *   <li><b>같은 컬럼의 check-then-act</b> — 숨김 해제는 "지금 숨김인가"를 확인한 뒤 해제한다.
 *       그 사이에 새 신고의 숨김 조치가 끼어들면, 해제가 나중에 커밋되며 방금 내려진 조치를
 *       되돌린다. 두 조치 모두 {@code is_hidden}을 쓰므로 부분 UPDATE로는 막을 수 없다.</li>
 *   <li><b>삭제 여부 확인 후의 조치</b> — 조치·수정 경로는 모두 잠금 뒤에 {@code !isDeleted()}를
 *       확인한다. 잠그지 않으면 스냅샷이 삭제 이전 상태를 보고 이미 사라진 글에 조치를 적용한다.</li>
 * </ul>
 *
 * <p>단위 테스트는 행 잠금과 커밋 순서를 재현하지 못해 이 경쟁을 잡을 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class UsedProductReportActionConcurrencyIntegrationTest extends IntegrationTestSupport {

    private final UsedProductReportActionExecutor reportActionExecutor;
    private final AdminUsedProductService adminUsedProductService;
    private final UsedProductService usedProductService;
    private final FavoriteService favoriteService;
    private final UsedProductRepository usedProductRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;

    private Long sellerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        Account seller = accountRepository.save(Account.createUser(
                "report-seller-" + tag + "@test.com", "encoded_pw", "판매자", "신고판매자" + tag, "010-2222-2222"));
        Account viewer = accountRepository.save(Account.createUser(
                "report-viewer-" + tag + "@test.com", "encoded_pw", "찜한사람", "신고찜꾼" + tag, "010-1111-1111"));
        sellerId = seller.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168" + tag, "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기" + tag, 1));

        productId = usedProductRepository.saveAndFlush(UsedProduct.create(
                seller, category, region, "신고 대상 글", "본문",
                UsedProductPriceType.FIXED, new BigDecimal("10000"))).getUsedProductId();

        // 삭제 조치가 정리해야 할 찜 1건.
        favoriteService.toggleFavorite(viewer.getAccountId(), toggleRequest());
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        deleteAccountsAbsorbingLateNotifications();
    }

    // 조치 알림은 @TransactionalEventListener(AFTER_COMMIT) + @Async라 테스트 본문이 끝난 뒤에
    // 들어올 수 있다. notification은 account를 FK로 참조하므로, 늦게 도착한 알림이 하나라도
    // 남아 있으면 계정 삭제가 막히고 다음 테스트가 엉뚱한 오류로 죽는다.
    private void deleteAccountsAbsorbingLateNotifications() {
        for (int attempt = 0; attempt < 20; attempt++) {
            notificationRepository.deleteAllInBatch();
            try {
                accountRepository.deleteAll();
                return;
            } catch (DataIntegrityViolationException retryable) {
                sleepQuietly(100);
            }
        }
        throw new IllegalStateException("비동기 알림이 계속 도착해 테스트 계정을 정리하지 못했다");
    }

    // 신고 조치 실행은 호출자의 트랜잭션을 전제한다(@Transactional 없음).
    // 트랜잭션 밖에서 부르면 비관적 잠금 조회 단계에서 바로 실패해 경쟁 자체가 만들어지지 않는다.
    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @Test
    void 숨김_해제_중에_들어온_신고_숨김_조치는_유실되지_않는다() throws Exception {
        // given: 이전 신고로 이미 숨겨진 글. 관리자가 오판이라 판단해 해제하는 사이,
        // 새 신고에 대한 숨김 조치가 들어온다.
        hideDirectly();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "used_product",
                () -> adminUsedProductService.show(productId),
                () -> inTransaction(() ->
                        reportActionExecutor.execute(ReportAction.HIDE_POST, productId, sellerId, "재신고")),
                failure);

        // then: 나중에 커밋된 숨김이 최종 상태여야 한다.
        // 잠금이 없으면 숨김 조치는 "이미 숨김"으로 보고 아무 컬럼도 쓰지 않고,
        // 뒤이어 해제가 커밋돼 방금 내려진 조치가 사라진다.
        assertThat(failure.get()).isNull();
        assertThat(product().isHidden())
                .as("나중에 내려진 숨김 조치가 해제에 덮이면 안 된다")
                .isTrue();
    }

    @Test
    void 신고_삭제_조치_중에_들어온_판매자_삭제는_찾을_수_없음으로_끝난다() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "used_product",
                () -> reportActionExecutor.execute(ReportAction.DELETE_POST, productId, sellerId, "삭제 조치"),
                () -> usedProductService.delete(sellerId, productId),
                failure);

        // then: 판매자 삭제는 잠금 해제 후 이미 삭제된 글을 보고 멈춘다.
        assertThat(failure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);

        assertThat(product().isDeleted()).isTrue();
        assertThat(favoriteRepository.countByRefTypeAndRefId(
                FavoriteRefType.USED_PRODUCT, productId))
                .as("공유 DB라 전역 count 대신 이 게시글에 달린 찜만 센다")
                .isZero();
        assertThat(product().getFavoriteCount()).isZero();
    }

    @Test
    void 판매자_삭제_중에_들어온_신고_숨김_조치는_사라진_글을_숨기지_않는다() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        raceOnLock(transactionManager, "used_product",
                () -> usedProductService.delete(sellerId, productId),
                () -> inTransaction(() ->
                        reportActionExecutor.execute(ReportAction.HIDE_POST, productId, sellerId, "숨김 조치")),
                failure);

        // then: 조치는 대상이 사라졌다고 응답해야 한다.
        // 잠금이 없으면 삭제 이전 스냅샷을 보고 조치가 성공해, 관리자에게는 처리된 것으로
        // 기록되지만 실제로는 이미 지워진 글이다.
        assertThat(failure.get())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);

        UsedProduct product = product();
        assertThat(product.isDeleted()).isTrue();
        assertThat(product.isHidden())
                .as("이미 삭제된 글에 숨김이 적용되면 안 된다")
                .isFalse();
    }

    // 신고 조치 경로를 거치지 않고 초기 상태만 숨김으로 만든다.
    private void hideDirectly() {
        UsedProduct product = usedProductRepository.findById(productId).orElseThrow();
        product.hide();
        usedProductRepository.saveAndFlush(product);
    }

    private UsedProduct product() {
        return usedProductRepository.findById(productId).orElseThrow();
    }

    private FavoriteToggleRequestDto toggleRequest() {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", productId);
        return request;
    }
}
