package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks private ReportService reportService;

    @Mock private ReportRepository reportRepository;
    @Mock private AccountRepository accountRepository;
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
        when(accountRepository.findById(reporterId)).thenReturn(Optional.of(reporter));

        ReportTargetSnapshotDto target = ReportTargetSnapshotDto.builder()
                .targetType(ReportTargetType.COMMUNITY_POST)
                .targetId(targetId)
                .exists(true)
                .title("신고 당시 제목")
                .content("삭제돼도 남아야 하는 전체 본문")
                .ownerAccountId(2L)
                .build();
        when(reportTargetResolver.resolveForCreation(ReportTargetType.COMMUNITY_POST, targetId))
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
}
