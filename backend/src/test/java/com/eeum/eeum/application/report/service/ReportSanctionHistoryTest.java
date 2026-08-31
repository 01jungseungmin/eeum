package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportProcessRequestDto;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSanctionHistoryTest {

    @InjectMocks private AdminReportService adminReportService;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportTargetResolver reportTargetResolver;
    @Mock private ReportActionDispatcher reportActionDispatcher;
    @Mock private SanctionHistoryService sanctionHistoryService;

    @Test
    void 작성자_경고는_실제_회원_ID로_신고_제재_이력을_기록한다() {
        // Given
        Report report = report(ReportTargetType.COMMUNITY_POST, 40L);
        ReportProcessRequestDto request = request(ReportAction.WARN_AUTHOR, "경고 조치");
        when(reportRepository.findByReportIdForUpdate(100L)).thenReturn(Optional.of(report));
        when(reportActionDispatcher.execute(
                ReportTargetType.COMMUNITY_POST,
                ReportAction.WARN_AUTHOR,
                40L,
                null,
                "경고 조치"
        )).thenReturn(30L);
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        // When
        adminReportService.processReport(100L, 1L, request);

        // Then
        verify(sanctionHistoryService).recordReportAction(
                SanctionTargetType.ACCOUNT,
                30L,
                SanctionAction.WARN,
                "경고 조치",
                1L,
                100L
        );
    }

    @Test
    void 상점_정지는_dispatcher가_반환한_소유자_ID가_아닌_신고_대상_상점_ID로_기록한다() {
        // Given
        Report report = report(ReportTargetType.STORE, 50L);
        ReportProcessRequestDto request = request(ReportAction.SUSPEND_STORE, "상점 정지");
        when(reportRepository.findByReportIdForUpdate(100L)).thenReturn(Optional.of(report));
        when(reportActionDispatcher.execute(
                ReportTargetType.STORE,
                ReportAction.SUSPEND_STORE,
                50L,
                null,
                "상점 정지"
        )).thenReturn(30L);
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        // When
        adminReportService.processReport(100L, 1L, request);

        // Then
        verify(sanctionHistoryService).recordReportAction(
                SanctionTargetType.STORE,
                50L,
                SanctionAction.SUSPEND,
                "상점 정지",
                1L,
                100L
        );
    }

    @Test
    void 게시글_숨김은_회원과_상점_제재_이력을_기록하지_않는다() {
        // Given
        Report report = report(ReportTargetType.COMMUNITY_POST, 40L);
        ReportProcessRequestDto request = request(ReportAction.HIDE_POST, "게시글 숨김");
        when(reportRepository.findByReportIdForUpdate(100L)).thenReturn(Optional.of(report));
        when(reportActionDispatcher.execute(
                ReportTargetType.COMMUNITY_POST,
                ReportAction.HIDE_POST,
                40L,
                null,
                "게시글 숨김"
        )).thenReturn(30L);
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        // When
        adminReportService.processReport(100L, 1L, request);

        // Then
        verify(sanctionHistoryService, never()).recordReportAction(
                any(), any(), any(), any(), any(), any()
        );
    }

    private Report report(ReportTargetType targetType, Long targetId) {
        Account reporter = Account.createUser(
                "reporter@test.com",
                "encoded",
                "신고자",
                "신고자닉",
                "010-1111-1111"
        );
        Report report = Report.create(
                reporter,
                targetType,
                targetId,
                ReportReason.ABUSE,
                "신고 내용"
        );
        ReflectionTestUtils.setField(report, "reportId", 100L);
        return report;
    }

    private ReportProcessRequestDto request(ReportAction action, String adminNote) {
        ReportProcessRequestDto request = new ReportProcessRequestDto();
        ReflectionTestUtils.setField(request, "action", action);
        ReflectionTestUtils.setField(request, "adminNote", adminNote);
        return request;
    }
}
