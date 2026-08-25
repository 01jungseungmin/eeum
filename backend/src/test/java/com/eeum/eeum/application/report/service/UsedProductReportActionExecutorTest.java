package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsedProductReportActionExecutorTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 1L;

    @Mock private UsedProductRepository usedProductRepository;
    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private FavoriteService favoriteService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UsedProductReportActionExecutor executor;

    @Test
    void 담당하는_신고_대상은_중고_게시글이다() {
        // 디스패처가 이 값으로 자동 등록한다
        assertThat(executor.targetType()).isEqualTo(ReportTargetType.USED_PRODUCT);
    }

    // ─────────────────── 숨김 ───────────────────

    @Test
    void 숨김_조치는_노출만_막고_거래_상태는_건드리지_않는다() {
        // given — 숨김은 노출 정책이고 예약·판매완료는 거래 사실이다
        UsedProduct product = product();
        product.reserve(null);
        givenProductForUpdate(product);

        // when
        Long sellerId = executor.execute(ReportAction.HIDE_POST, PRODUCT_ID, null, "부적절한 게시물");

        // then
        assertThat(product.isHidden()).isTrue();
        assertThat(product.getStatus()).isEqualTo(UsedProductStatus.RESERVED);
        assertThat(sellerId).isEqualTo(SELLER_ID);
    }

    // ─────────────────── 삭제 ───────────────────

    @Test
    void 삭제_조치는_soft_delete하고_찜까지_정리한다() {
        // given — 사용자가 지우든 관리자가 지우든 결과는 같아야 한다
        UsedProduct product = product();
        givenProductForUpdate(product);

        // when
        executor.execute(ReportAction.DELETE_POST, PRODUCT_ID, null, "판매 금지 품목");

        // then
        assertThat(product.isDeleted()).isTrue();
        verify(favoriteService).deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, PRODUCT_ID);
        verify(usedProductRepository, never()).delete(any());
    }

    // ─────────────────── 계정 조치 ───────────────────

    @Test
    void 경고_정지는_계정_조치_서비스에_위임한다() {
        // given — 저장된 판매자 ID가 있으면 게시글을 다시 읽지 않는다
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, SELLER_ID))
                .thenReturn(SELLER_ID);

        // when
        Long result = executor.execute(ReportAction.SUSPEND_AUTHOR, PRODUCT_ID, SELLER_ID, "반복 위반");

        // then
        assertThat(result).isEqualTo(SELLER_ID);
        verify(usedProductRepository, never()).findByUsedProductIdForUpdate(any());
    }

    @Test
    void 저장된_판매자_ID가_없으면_게시글에서_찾되_상품을_잠그지_않는다() {
        // 폴백에서 상품을 잠그면 곧바로 계정을 잠그게 되어 used_product → account 순서가 된다.
        // 판매자 경로(AccountWriteGuard)는 account → used_product라 정반대여서 교착이 난다.
        // 이 경로는 상품을 수정하지 않으므로 잠금 없는 조회로 대상만 찾는다.
        when(usedProductRepository.findSellerAccountIdByUsedProductId(PRODUCT_ID))
                .thenReturn(Optional.of(SELLER_ID));
        when(reportedAccountActionService.apply(ReportAction.WARN_AUTHOR, SELLER_ID))
                .thenReturn(SELLER_ID);

        executor.execute(ReportAction.WARN_AUTHOR, PRODUCT_ID, null, "경고");

        verify(reportedAccountActionService).apply(ReportAction.WARN_AUTHOR, SELLER_ID);
        verify(usedProductRepository, never()).findByUsedProductIdForUpdate(any());
    }

    @Test
    void 저장된_판매자_ID가_없고_게시글도_없으면_대상_없음으로_끝난다() {
        // 잠금 조회에서 잠금 없는 조회로 바꿔도 삭제 필터는 유지돼야 한다.
        when(usedProductRepository.findSellerAccountIdByUsedProductId(PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executor.execute(ReportAction.WARN_AUTHOR, PRODUCT_ID, null, "경고"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);

        verify(reportedAccountActionService, never()).apply(any(), any());
    }

    // ─────────────────── 알림 ───────────────────

    @Test
    void 조치_후_판매자에게_알림_이벤트를_발행한다() {
        givenProductForUpdate(product());

        executor.execute(ReportAction.HIDE_POST, PRODUCT_ID, null, "부적절한 게시물");

        ArgumentCaptor<ReportActionNotificationEvent> captor =
                ArgumentCaptor.forClass(ReportActionNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ReportActionNotificationEvent event = captor.getValue();
        assertThat(event.targetAccountId()).isEqualTo(SELLER_ID);
        assertThat(event.refType()).isEqualTo(NotificationRefType.USED_PRODUCT);
        assertThat(event.refId()).isEqualTo(PRODUCT_ID);
        assertThat(event.actionLabel()).isEqualTo("중고 게시글 숨김");
        assertThat(event.adminNote()).isEqualTo("부적절한 게시물");
    }

    // ─────────────────── 거부 경로 ───────────────────

    @Test
    void 중고_게시글에_맞지_않는_조치는_거부한다() {
        assertThatThrownBy(() -> executor.execute(
                ReportAction.DELETE_COMMENT, PRODUCT_ID, SELLER_ID, "잘못된 조치"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);

        verify(eventPublisher, never()).publishEvent(any(ReportActionNotificationEvent.class));
    }

    @Test
    void 이미_삭제된_게시글은_조치할_수_없다() {
        // given — 조치 대상이 사라졌다는 사실을 관리자에게 알려야 한다
        UsedProduct deleted = product();
        deleted.softDelete();
        givenProductForUpdate(deleted);

        assertThatThrownBy(() -> executor.execute(ReportAction.HIDE_POST, PRODUCT_ID, null, "숨김"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
    }

    @Test
    void 존재하지_않는_게시글_조치는_거부한다() {
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(ReportAction.DELETE_POST, PRODUCT_ID, null, "삭제"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);

        verify(favoriteService, never()).deleteAllByRefTypeAndRefId(any(), any());
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenProductForUpdate(UsedProduct product) {
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    private UsedProduct product() {
        Account seller = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(seller, "accountId", SELLER_ID);

        UsedProduct product = UsedProduct.create(
                seller, Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }
}
