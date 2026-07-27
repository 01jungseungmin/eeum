package com.eeum.eeum.application.notification.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.store.event.StoreReviewCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreReviewNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StoreReviewNotificationListener listener;

    @Test
    void 리뷰_작성시_상점_사장에게_STORE_REVIEW_알림을_생성한다() {
        // given
        Long ownerAccountId = 10L;
        Long reviewId = 33L;
        StoreReviewCreatedEvent event = new StoreReviewCreatedEvent(
                ownerAccountId, "홍길동", "테스트상점", 1L, reviewId);
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);

        // when
        listener.onReviewCreated(event);

        // then
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto dto = captor.getValue();
        assertThat(dto.getAccountId()).isEqualTo(ownerAccountId);
        assertThat(dto.getType()).isEqualTo(NotificationType.STORE_REVIEW);
        assertThat(dto.getRefType()).isEqualTo(NotificationRefType.STORE_REVIEW);
        assertThat(dto.getRefId()).isEqualTo(reviewId);
    }
}
