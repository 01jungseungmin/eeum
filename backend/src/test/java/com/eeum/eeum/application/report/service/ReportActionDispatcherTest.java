package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportActionDispatcherTest {

    private static final Long TARGET_ID = 10L;
    private static final Long OWNER_ACCOUNT_ID = 20L;

    @Test
    void 신고_대상_타입에_맞는_실행기에_조치를_위임한다() {
        // given
        ReportTargetActionExecutor executor = mock(ReportTargetActionExecutor.class);
        when(executor.targetType()).thenReturn(ReportTargetType.COMMUNITY_POST);
        when(executor.execute(
                ReportAction.HIDE_POST,
                TARGET_ID,
                OWNER_ACCOUNT_ID,
                "정책 위반"
        )).thenReturn(OWNER_ACCOUNT_ID);
        ReportActionDispatcher dispatcher = new ReportActionDispatcher(List.of(executor));

        // when
        Long result = dispatcher.execute(
                ReportTargetType.COMMUNITY_POST,
                ReportAction.HIDE_POST,
                TARGET_ID,
                OWNER_ACCOUNT_ID,
                "정책 위반"
        );

        // then
        assertThat(result).isEqualTo(OWNER_ACCOUNT_ID);
        verify(executor).execute(
                ReportAction.HIDE_POST,
                TARGET_ID,
                OWNER_ACCOUNT_ID,
                "정책 위반"
        );
    }

    @Test
    void 대상_타입을_처리할_실행기가_없으면_조치를_거부한다() {
        // given
        ReportActionDispatcher dispatcher = new ReportActionDispatcher(List.of());

        // when & then
        assertThatThrownBy(() -> dispatcher.execute(
                ReportTargetType.STORE,
                ReportAction.SUSPEND_AUTHOR,
                TARGET_ID,
                OWNER_ACCOUNT_ID,
                "정책 위반"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
    }
}
