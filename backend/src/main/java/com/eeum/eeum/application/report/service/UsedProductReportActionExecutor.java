package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 중고 게시글 신고에 대한 관리자 조치.
 *
 * {@code ReportActionDispatcher}가 {@code List<ReportTargetActionExecutor>}를 주입받아
 * {@link #targetType()} 기준으로 자동 등록하므로 별도 배선이 필요 없다.
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

    private Long deleteProduct(Long usedProductId) {
        UsedProduct product = getProductForUpdate(usedProductId);
        Long sellerAccountId = product.getSeller().getAccountId();

        product.softDelete();

        // 사용자가 지우든 관리자가 지우든 결과는 같아야 한다 —
        // 정리하지 않으면 다른 사용자의 찜 목록에 사라진 글이 남는다.
        favoriteService.deleteAllByRefTypeAndRefId(FavoriteRefType.USED_PRODUCT, usedProductId);

        return sellerAccountId;
    }

    // 신고 시점에 저장해 둔 판매자 ID를 우선 쓴다. 글이 지워졌어도 조치 대상을 찾을 수 있다.
    private Long resolveSellerAccountId(Long usedProductId, Long storedSellerAccountId) {
        if (storedSellerAccountId != null) {
            return storedSellerAccountId;
        }
        return getProductForUpdate(usedProductId).getSeller().getAccountId();
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
