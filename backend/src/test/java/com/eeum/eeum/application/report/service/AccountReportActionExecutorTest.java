package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.event.ReportActionNotificationEvent;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;

@ExtendWith(MockitoExtension.class)
class AccountReportActionExecutorTest {

    @InjectMocks private AccountReportActionExecutor executor;

    @Mock private ReportedAccountActionService reportedAccountActionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 회원_신고의_정지_조치는_targetId_회원에게_적용한다() {
        // Given
        when(reportedAccountActionService.apply(ReportAction.SUSPEND_AUTHOR, 10L)).thenReturn(10L);

        // When
        Long result = executor.execute(ReportAction.SUSPEND_AUTHOR, 10L, 20L, "반복 위반");

        // Then
        assertThat(executor.targetType()).isEqualTo(ReportTargetType.ACCOUNT);
        assertThat(result).isEqualTo(10L);
        verify(reportedAccountActionService).apply(ReportAction.SUSPEND_AUTHOR, 10L);
        verify(eventPublisher).publishEvent(isA(ReportActionNotificationEvent.class));
    }

    @Test
    void 회원_신고에는_콘텐츠_삭제_조치를_적용할_수_없다() {
        assertThatThrownBy(() ->
                executor.execute(ReportAction.DELETE_COMMENT, 10L, 10L, "잘못된 조치"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
    }
}
