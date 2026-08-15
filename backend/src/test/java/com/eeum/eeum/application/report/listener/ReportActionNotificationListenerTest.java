package com.eeum.eeum.application.report.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportActionNotificationListenerTest {

    @InjectMocks private ReportActionNotificationListener listener;

    @Mock private NotificationService notificationService;

    @Test
    void 상점과_회원_신고_조치를_필수_시스템_알림으로_전달한다() {
        // Given
        ReportActionNotificationEvent event = new ReportActionNotificationEvent(
                10L,
                NotificationRefType.STORE,
                20L,
                "상점 정지",
                "허위 정보 반복 게시"
        );

        // When
        listener.onReportAction(event);

        // Then
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotification(captor.capture());
        NotificationCreateRequestDto request = captor.getValue();
        assertThat(request.getAccountId()).isEqualTo(10L);
        assertThat(request.getType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
        assertThat(request.getRefType()).isEqualTo(NotificationRefType.STORE);
        assertThat(request.getRefId()).isEqualTo(20L);
        assertThat(request.getContent())
                .contains("상점 정지")
                .contains("허위 정보 반복 게시");
    }
}
