package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewImage;
import com.eeum.eeum.domain.store.event.StoreReviewAdminActionEvent;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewImageRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoreReviewReportActionExecutor implements ReportTargetActionExecutor {

    private final StoreReviewRepository reviewRepository;
    private final StoreReviewImageRepository imageRepository;
    private final StoreReviewReplyRepository replyRepository;
    private final StoreRepository storeRepository;
    private final ReportedAccountActionService reportedAccountActionService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReportTargetType targetType() {
        return ReportTargetType.STORE_REVIEW;
    }

    @Override
    public Long execute(
            ReportAction action,
            Long reviewId,
            Long storedAuthorAccountId,
            String adminNote
    ) {
        Long authorAccountId = switch (action) {
            case DELETE_STORE_REVIEW -> deleteReview(reviewId);
            case WARN_AUTHOR, SUSPEND_AUTHOR -> reportedAccountActionService.apply(
                    action,
                    resolveAuthorAccountId(reviewId, storedAuthorAccountId)
            );
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };

        eventPublisher.publishEvent(new StoreReviewAdminActionEvent(
                authorAccountId,
                reviewId,
                actionLabel(action),
                adminNote
        ));
        return authorAccountId;
    }

    private Long deleteReview(Long reviewId) {
        Long storeId = reviewRepository.findStoreIdByStorereviewId(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        Store store = storeRepository.findByIdWithPessimisticLock(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        StoreReview review = getReviewForUpdate(reviewId);
        Long authorAccountId = review.getAccount().getAccountId();

        // 이미지 행만 지우면 FileObject가 ATTACHED로 남아 정리 스케줄러가 회수하지 못한다.
        // 삭제 전에 key를 모아 두고, 행을 지운 뒤 정리 대상으로 전환한다.
        List<String> imageUrls = imageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId)
                .stream()
                .map(StoreReviewImage::getImageUrl)
                .toList();

        imageRepository.deleteAllByStoreReview_StorereviewId(reviewId);
        fileStorageService.scheduleAttachedObjectCleanup(imageUrls);
        replyRepository.deleteByStoreReview_StorereviewId(reviewId);
        reviewRepository.delete(review);
        reviewRepository.flush();

        double averageRating = reviewRepository.calculateAverageRating(storeId);
        int reviewCount = Math.toIntExact(reviewRepository.countByStoreId(storeId));
        store.updateRating(averageRating, reviewCount);

        return authorAccountId;
    }

    private Long resolveAuthorAccountId(Long reviewId, Long storedAuthorAccountId) {
        if (storedAuthorAccountId != null) {
            return storedAuthorAccountId;
        }
        return getReviewForUpdate(reviewId).getAccount().getAccountId();
    }

    private StoreReview getReviewForUpdate(Long reviewId) {
        return reviewRepository.findWithAccountAndStoreByStorereviewIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
    }

    private String actionLabel(ReportAction action) {
        return switch (action) {
            case DELETE_STORE_REVIEW -> "리뷰 삭제";
            case WARN_AUTHOR -> "작성자 경고";
            case SUSPEND_AUTHOR -> "작성자 정지";
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }
}
