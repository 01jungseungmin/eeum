package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreReportActionExecutor implements ReportTargetActionExecutor {

    private final StoreRepository storeRepository;
    private final ReportedAccountActionService reportedAccountActionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.STORE;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long storeId,
            Long storedOwnerAccountId,
            String adminNote
    ) {
        Long ownerAccountId = switch (action) {
            case SUSPEND_STORE -> suspendStore(storeId);
            case WARN_AUTHOR, SUSPEND_AUTHOR -> reportedAccountActionService.apply(
                    action,
                    resolveOwnerAccountId(storeId, storedOwnerAccountId)
            );
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
        eventPublisher.publishEvent(new ReportActionNotificationEvent(
                ownerAccountId,
                NotificationRefType.STORE,
                storeId,
                actionLabel(action),
                adminNote
        ));
        return ownerAccountId;
    }

    private Long suspendStore(Long storeId) {
        Store store = getStoreForUpdate(storeId);
        store.suspend();
        return store.getAccount().getAccountId();
    }

    private Long resolveOwnerAccountId(Long storeId, Long storedOwnerAccountId) {
        if (storedOwnerAccountId != null) {
            return storedOwnerAccountId;
        }
        return getStoreForUpdate(storeId).getAccount().getAccountId();
    }

    private Store getStoreForUpdate(Long storeId) {
        return storeRepository.findByIdWithPessimisticLock(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private String actionLabel(ReportAction action) {
        return switch (action) {
            case SUSPEND_STORE -> "상점 정지";
            case WARN_AUTHOR -> "상점 소유자 경고";
            case SUSPEND_AUTHOR -> "상점 소유자 정지";
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }
}
