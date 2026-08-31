package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.event.OperationFailureRecordedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationFailureNotificationListenerTest {

    private static final Long LOG_ID = 77L;

    @Mock private NotificationService notificationService;
    @Mock private AccountRepository accountRepository;
    @Mock private RateLimitService rateLimitService;

    @InjectMocks
    private OperationFailureNotificationListener listener;

    @Test
    void 실패가_기록되면_모든_관리자에게_알림이_생성된다() {
        // given
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of(1L, 2L, 3L));
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.REFUND, "PaymentService.cancelPayment", "PORTONE_TIMEOUT"));

        // then
        List<NotificationCreateRequestDto> sent = captureRequests();
        assertThat(sent).hasSize(3);
        assertThat(sent).extracting(NotificationCreateRequestDto::getAccountId)
                .containsExactly(1L, 2L, 3L);
        assertThat(sent).allSatisfy(dto -> {
            assertThat(dto.getType()).isEqualTo(NotificationType.OPERATION_FAILURE_DETECTED);
            assertThat(dto.getRefType()).isEqualTo(NotificationRefType.OPERATION_FAILURE);
            assertThat(dto.getRefId()).isEqualTo(LOG_ID);
        });
    }

    @Test
    void 알림은_해당_분류가_필터된_대시보드로_연결된다() {
        // given
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of(1L));
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.SCHEDULER, "OrderExpirationScheduler.expire", "NPE"));

        // then — 관리자가 알림을 눌렀을 때 해당 분류만 걸러진 목록이 열려야 한다
        NotificationCreateRequestDto dto = captureRequests().get(0);
        assertThat(dto.getLinkUrl()).isEqualTo("/admin/operations/failures?category=SCHEDULER");
        assertThat(dto.getContent())
                .contains("SCHEDULER")
                .contains("OrderExpirationScheduler.expire")
                .contains("NPE");
    }

    @Test
    void 같은_분류의_실패가_쿨다운_중이면_알림도_관리자_조회도_하지_않는다() {
        // given — PortOne 장애로 환불 실패가 연속 발생하는 상황
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(false);

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.REFUND, "PaymentService.cancelPayment", "PORTONE_TIMEOUT"));

        // then — 이력은 이미 저장돼 있고 알림만 생략된다.
        // 폭주 구간에서 건별 관리자 조회 쿼리가 나가지 않아야 한다.
        verify(notificationService, never()).createNotificationsBatch(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void 쿨다운_키는_분류별로_분리되어_다른_분류의_실패는_묻히지_않는다() {
        // given
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of(1L));
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.REFUND, "PaymentService.cancelPayment", "PORTONE_TIMEOUT"));

        // then
        verify(rateLimitService).tryAcquireCooldown(
                eq("rate-limit:operation-failure-alert:REFUND"),
                eq(OperationFailureNotificationListener.ALERT_COOLDOWN));
    }

    @Test
    void 관리자가_없으면_잡았던_쿨다운을_반납한다() {
        // given — 그대로 두면 이후 관리자가 생겨도 남은 쿨다운 동안 알림이 조용히 사라진다
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of());

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.PAYMENT_WEBHOOK, "PaymentService.handleWebhook", "INVALID_SIGNATURE"));

        // then
        verify(rateLimitService).releaseCooldown("rate-limit:operation-failure-alert:PAYMENT_WEBHOOK");
        verify(notificationService, never()).createNotificationsBatch(any());
    }

    @Test
    void 에러코드가_없어도_알림_본문이_비어_보이지_않는다() {
        // given
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of(1L));
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);

        // when
        listener.onOperationFailureRecorded(event(
                OperationFailureCategory.EXTERNAL_API, "NtsClient.verify", null));

        // then
        assertThat(captureRequests().get(0).getContent()).contains("원인 미상");
    }

    @Test
    void 분류가_없으면_서버가_거부하는_필터값_대신_전체_목록으로_연결한다() {
        // given — ?category=UNKNOWN을 붙이면 관리자가 알림을 눌렀을 때 목록 대신 400을 본다
        when(accountRepository.findAdminAccountIds()).thenReturn(List.of(1L));
        when(rateLimitService.tryAcquireCooldown(anyString(), any(Duration.class))).thenReturn(true);

        // when
        listener.onOperationFailureRecorded(
                new OperationFailureRecordedEvent(LOG_ID, null, "Unknown.operation", "E1"));

        // then
        assertThat(captureRequests().get(0).getLinkUrl())
                .isEqualTo("/admin/operations/failures");
    }

    // ─────────────────── 헬퍼 ───────────────────

    private OperationFailureRecordedEvent event(
            OperationFailureCategory category, String operation, String errorCode) {
        return new OperationFailureRecordedEvent(LOG_ID, category, operation, errorCode);
    }

    @SuppressWarnings("unchecked")
    private List<NotificationCreateRequestDto> captureRequests() {
        ArgumentCaptor<List<NotificationCreateRequestDto>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(notificationService).createNotificationsBatch(captor.capture());
        return captor.getValue();
    }
}
