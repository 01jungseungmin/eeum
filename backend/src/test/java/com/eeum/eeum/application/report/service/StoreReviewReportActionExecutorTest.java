package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.account.entity.Account;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreReviewReportActionExecutorTest {

    private static final Long REVIEW_ID = 10L;
    private static final Long STORE_ID = 20L;
    private static final Long AUTHOR_ID = 30L;

    @InjectMocks
    private StoreReviewReportActionExecutor executor;

    @Mock private StoreReviewRepository reviewRepository;
    @Mock private StoreReviewImageRepository imageRepository;
    @Mock private StoreReviewReplyRepository replyRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private FileStorageService fileStorageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 상점_리뷰_대상_실행기를_지원한다() {
        // when & then
        assertThat(executor.targetType()).isEqualTo(ReportTargetType.STORE_REVIEW);
    }

    @Test
    void 리뷰_삭제는_자식_데이터를_먼저_삭제하고_남은_리뷰로_평점을_재계산한다() {
        // given
        StoreReview review = createReview();
        Store store = review.getStore();
        when(reviewRepository.findStoreIdByStorereviewId(REVIEW_ID))
                .thenReturn(Optional.of(STORE_ID));
        when(reviewRepository.findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID))
                .thenReturn(Optional.of(review));
        when(storeRepository.findByIdWithPessimisticLock(STORE_ID)).thenReturn(Optional.of(store));
        when(reviewRepository.calculateAverageRating(STORE_ID)).thenReturn(4.25);
        when(reviewRepository.countByStoreId(STORE_ID)).thenReturn(2L);

        // when
        Long result = executor.execute(
                ReportAction.DELETE_STORE_REVIEW,
                REVIEW_ID,
                AUTHOR_ID,
                "조작된 리뷰"
        );

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        assertThat(store.getRating()).isEqualTo(4.3);
        assertThat(store.getReviewCount()).isEqualTo(2);

        InOrder order = inOrder(
                reviewRepository,
                storeRepository,
                imageRepository,
                replyRepository
        );
        order.verify(reviewRepository).findStoreIdByStorereviewId(REVIEW_ID);
        order.verify(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        order.verify(reviewRepository)
                .findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID);
        order.verify(imageRepository).deleteAllByStoreReview_StorereviewId(REVIEW_ID);
        order.verify(replyRepository).deleteByStoreReview_StorereviewId(REVIEW_ID);
        order.verify(reviewRepository).delete(review);
        order.verify(reviewRepository).flush();
        order.verify(reviewRepository).calculateAverageRating(STORE_ID);
        order.verify(reviewRepository).countByStoreId(STORE_ID);

        ArgumentCaptor<StoreReviewAdminActionEvent> eventCaptor =
                ArgumentCaptor.forClass(StoreReviewAdminActionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().targetAccountId()).isEqualTo(AUTHOR_ID);
        assertThat(eventCaptor.getValue().reviewId()).isEqualTo(REVIEW_ID);
        assertThat(eventCaptor.getValue().actionLabel()).isEqualTo("리뷰 삭제");
        assertThat(eventCaptor.getValue().adminNote()).isEqualTo("조작된 리뷰");
    }

    @Test
    void 리뷰_삭제는_첨부된_이미지도_정리_대상으로_전환한다() {
        // given — 이미지 행만 지우면 FileObject가 ATTACHED로 남아 스케줄러가 회수하지 못한다.
        StoreReview review = createReview();
        Store store = review.getStore();
        when(reviewRepository.findStoreIdByStorereviewId(REVIEW_ID))
                .thenReturn(Optional.of(STORE_ID));
        when(reviewRepository.findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID))
                .thenReturn(Optional.of(review));
        when(storeRepository.findByIdWithPessimisticLock(STORE_ID)).thenReturn(Optional.of(store));
        when(imageRepository.findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(REVIEW_ID))
                .thenReturn(List.of(
                        StoreReviewImage.create(review, "stores/30/first.webp", 0, true),
                        StoreReviewImage.create(review, "stores/30/second.webp", 1, false)
                ));

        // when
        executor.execute(ReportAction.DELETE_STORE_REVIEW, REVIEW_ID, AUTHOR_ID, "조작된 리뷰");

        // then — 삭제 전에 key를 읽어 두고, 행을 지운 뒤 정리 대상으로 전환한다.
        InOrder order = inOrder(imageRepository, fileStorageService);
        order.verify(imageRepository)
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(REVIEW_ID);
        order.verify(imageRepository).deleteAllByStoreReview_StorereviewId(REVIEW_ID);
        order.verify(fileStorageService).scheduleAttachedObjectCleanup(
                List.of("stores/30/first.webp", "stores/30/second.webp"));
    }

    @Test
    void 작성자_경고는_신고_접수_시_저장한_작성자_ID를_우선_사용한다() {
        // given
        when(reportedAccountActionService.apply(ReportAction.WARN_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(
                ReportAction.WARN_AUTHOR,
                REVIEW_ID,
                AUTHOR_ID,
                "1차 경고"
        );

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(reviewRepository, never())
                .findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID);
        verify(reportedAccountActionService).apply(ReportAction.WARN_AUTHOR, AUTHOR_ID);
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.isA(
                StoreReviewAdminActionEvent.class));
    }

    @Test
    void 스냅샷_작성자가_없으면_리뷰를_잠금_조회해_작성자를_정지한다() {
        // given
        StoreReview review = createReview();
        when(reviewRepository.findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID))
                .thenReturn(Optional.of(review));
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID))
                .thenReturn(AUTHOR_ID);

        // when
        Long result = executor.execute(
                ReportAction.SUSPEND_AUTHOR,
                REVIEW_ID,
                null,
                "반복 위반"
        );

        // then
        assertThat(result).isEqualTo(AUTHOR_ID);
        verify(reviewRepository).findWithAccountAndStoreByStorereviewIdForUpdate(REVIEW_ID);
        verify(reportedAccountActionService).apply(ReportAction.SUSPEND_AUTHOR, AUTHOR_ID);
    }

    @Test
    void 삭제된_리뷰에_콘텐츠_삭제_조치를_적용할_수_없다() {
        // given
        when(reviewRepository.findStoreIdByStorereviewId(REVIEW_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> executor.execute(
                ReportAction.DELETE_STORE_REVIEW,
                REVIEW_ID,
                AUTHOR_ID,
                "조작된 리뷰"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_TARGET_NOT_AVAILABLE);
        verify(eventPublisher, never()).publishEvent(
                org.mockito.ArgumentMatchers.isA(StoreReviewAdminActionEvent.class));
    }

    @Test
    void 상점_리뷰에_허용되지_않는_조치는_거부한다() {
        // when & then
        assertThatThrownBy(() -> executor.execute(
                ReportAction.DELETE_POST,
                REVIEW_ID,
                AUTHOR_ID,
                "허용되지 않음"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        verify(eventPublisher, never()).publishEvent(
                org.mockito.ArgumentMatchers.isA(StoreReviewAdminActionEvent.class));
    }

    private StoreReview createReview() {
        Account owner = Account.createUser(
                "owner@test.com",
                "encoded",
                "사장",
                "사장닉",
                "010-1111-2222"
        );
        Store store = Store.createForOwnerSignup(owner, "상점", "서울시", "02-1111-2222");
        ReflectionTestUtils.setField(store, "storeId", STORE_ID);

        Account author = Account.createUser(
                "author@test.com",
                "encoded",
                "작성자",
                "작성자닉",
                "010-3333-4444"
        );
        ReflectionTestUtils.setField(author, "accountId", AUTHOR_ID);

        StoreReview review = StoreReview.createForOrder(store, author, null, 5, "리뷰 내용");
        ReflectionTestUtils.setField(review, "storereviewId", REVIEW_ID);
        return review;
    }
}
