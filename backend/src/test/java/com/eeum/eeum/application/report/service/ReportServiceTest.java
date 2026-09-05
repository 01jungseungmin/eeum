package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.exception.NotFoundException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks private ReportService reportService;

    @Mock private ReportRepository reportRepository;
    @Mock private AccountWriteGuard accountWriteGuard;
    @Mock private ReportTargetResolver reportTargetResolver;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 신고_접수_시점의_대상_제목과_전체_본문을_보존한다() {
        // given
        Long reporterId = 1L;
        Long targetId = 10L;
        Account reporter = Account.createUser(
                "reporter@test.com", "encoded", "신고자", "신고자닉", "010-0000-0000");
        ReflectionTestUtils.setField(reporter, "accountId", reporterId);
        when(accountWriteGuard.lockActive(reporterId)).thenReturn(reporter);

        ReportTargetSnapshotDto target = ReportTargetSnapshotDto.builder()
                .targetType(ReportTargetType.COMMUNITY_POST)
                .targetId(targetId)
                .exists(true)
                .title("신고 당시 제목")
                .content("삭제돼도 남아야 하는 전체 본문")
                .ownerAccountId(2L)
                .build();
        when(reportTargetResolver.resolveForCreation(
                eq(ReportTargetType.COMMUNITY_POST), eq(targetId), any()))
                .thenReturn(target);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "reportId", 100L);
            return report;
        });

        ReportCreateRequestDto request = new ReportCreateRequestDto();
        ReflectionTestUtils.setField(request, "targetType", ReportTargetType.COMMUNITY_POST);
        ReflectionTestUtils.setField(request, "targetId", targetId);
        ReflectionTestUtils.setField(request, "reason", ReportReason.ABUSE);
        ReflectionTestUtils.setField(request, "content", "신고 사유");

        // when
        reportService.createReport(reporterId, request);

        // then
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        org.mockito.Mockito.verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetTitleSnapshot()).isEqualTo("신고 당시 제목");
        assertThat(captor.getValue().getTargetContentSnapshot())
                .isEqualTo("삭제돼도 남아야 하는 전체 본문");
        assertThat(captor.getValue().getTargetOwnerAccountIdSnapshot()).isEqualTo(2L);
    }

    @Test
    void 남의_신고_상세는_없는_신고와_같은_응답을_준다() {
        // given — 남의 신고에 403, 없는 신고에 404를 주면 그 차이로
        // 임의의 reportId에 신고가 존재하는지 알아낼 수 있다
        when(reportRepository.findByReportIdAndReporter_AccountId(99L, 1L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reportService.getMyReportDetail(1L, 99L))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }
}
