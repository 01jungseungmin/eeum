package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 중고 게시글 신고에 대한 관리자 조치.
 *
 * ReportActionDispatcher가 List<ReportTargetActionExecutor>를 주입받아
 * #targetType() 기준으로 자동 등록하므로 별도 배선이 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class UsedProductReportActionExecutor implements ReportTargetActionExecutor {

    private final UsedProductRepository usedProductRepository;
    private final ReportedAccountActionService reportedAccountActionService;
    private final FavoriteService favoriteService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.USED_PRODUCT;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long usedProductId,
            Long storedSellerAccountId,
            String adminNote
    ) {
        Long sellerAccountId = switch (action) {
            case HIDE_POST -> hideProduct(usedProductId);
            case DELETE_POST -> deleteProduct(usedProductId);
            case WARN_AUTHOR, SUSPEND_AUTHOR -> reportedAccountActionService.apply(
                    action,
                    resolveSellerAccountId(usedProductId, storedSellerAccountId)
            );
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };

        eventPublisher.publishEvent(new ReportActionNotificationEvent(
                sellerAccountId,
                NotificationRefType.USED_PRODUCT,
                usedProductId,
                actionLabel(action),
                adminNote
        ));
        return sellerAccountId;
    }

    // 거래 상태는 건드리지 않는다 — 숨김은 노출 정책이고, 예약·판매완료는 거래 사실이다.
    private Long hideProduct(Long usedProductId) {
        UsedProduct product = getProductForUpdate(usedProductId);
        product.hide();
        return product.getSeller().getAccountId();
    }

    /**
     * 게시글 삭제. 예약을 먼저 정리한다.
     *
     * 사용자 삭제 경로는 예약 중인 글의 삭제를 아예 막지만, 관리자 조치는 불법 게시글을
     * 즉시 내려야 해서 막을 수 없다. 구매자가 겪는 상황은 같으므로 차단 대신 예약을 취소하고
     * 알린다. 정리하지 않으면 예약이 RESERVED로 영구히 남는다 — 삭제된 글은 상태 전이 경로가
     * 걸러내 판매자도 그 예약을 취소할 수 없다.
     */
    private Long deleteProduct(Long usedProductId) {
        UsedProduct product = getProductForUpdate(usedProductId);
        Long sellerAccountId = product.getSeller().getAccountId();

        cancelReservationIfAny(product);
        product.softDelete();

        // 사용자가 지우든 관리자가 지우든 결과는 같아야 한다 —
        // 정리하지 않으면 다른 사용자의 찜 목록에 사라진 글이 남는다.
        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, usedProductId);

        return sellerAccountId;
    }

    /** 조치 대상 판매자를 찾는다. 신고 접수 시 저장해 둔 ID를 우선 쓰므로,. */
    // 상대 없는 "예약중"도 되돌린다 — 통보만 상대가 있을 때 한다.
    private void cancelReservationIfAny(UsedProduct product) {
        if (product.getStatus() != UsedProductStatus.RESERVED) {
            return;
        }

        Account buyer = product.getBuyer();
        product.cancelReservation();
        if (buyer == null) {
            return;
        }

        eventPublisher.publishEvent(new UsedProductReservationCancelledEvent(
                product.getUsedProductId(), buyer.getAccountId(), product.getTitle()));
    }

    private Long resolveSellerAccountId(Long usedProductId, Long storedSellerAccountId) {
        if (storedSellerAccountId != null) {
            return storedSellerAccountId;
        }
        return usedProductRepository.findSellerAccountIdByUsedProductId(usedProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private UsedProduct getProductForUpdate(Long usedProductId) {
        return usedProductRepository.findByUsedProductIdForUpdate(usedProductId)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private String actionLabel(ReportAction action) {
        return switch (action) {
            case HIDE_POST -> "중고 게시글 숨김";
            case DELETE_POST -> "중고 게시글 삭제";
            case WARN_AUTHOR -> "판매자 경고";
            case SUSPEND_AUTHOR -> "판매자 정지";
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }
}
