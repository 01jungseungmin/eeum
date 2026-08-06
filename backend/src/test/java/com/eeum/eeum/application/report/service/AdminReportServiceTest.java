package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportDetailResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @InjectMocks
    private AdminReportService adminReportService;

    @Mock private ReportRepository reportRepository;
    @Mock private ReportTargetResolver reportTargetResolver;

    private static final Long REPORT_ID = 1L;
    private static final Long REPORTER_ID = 10L;
    private static final Long TARGET_ID = 40L;

    // ===================== 픽스처 헬퍼 =====================

    private Account createReporter() {
        Account account = Account.createUser(
                "reporter@test.com", "encoded-pw", "신고자", "신고닉", "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", REPORTER_ID);
        return account;
    }

    private Report createReport() {
        Report report = Report.create(
                createReporter(),
                ReportTargetType.COMMUNITY_POST,
                TARGET_ID,
                ReportReason.ABUSE,
                "욕설이 포함되어 있습니다"
        );
        ReflectionTestUtils.setField(report, "reportId", REPORT_ID);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.now().minusDays(1));
        return report;
    }

    private ReportTargetSnapshotDto createTargetSnapshot() {
        return ReportTargetSnapshotDto.builder()
                .targetType(ReportTargetType.COMMUNITY_POST)
                .targetId(TARGET_ID)
                .exists(true)
                .title("신고 대상 게시글")
                .contentPreview("부적절한 본문")
                .ownerAccountId(20L)
                .ownerName("박작성")
                .ownerNickname("작성자")
                .build();
    }

    // ===================== 신고 상세 조회 =====================

    @Test
    void 신고_상세_조회_시_신고자와_대상_정보가_모두_포함된다() {
        // Given
        Report report = createReport();
        when(reportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, TARGET_ID))
                .thenReturn(createTargetSnapshot());

        // When
        ReportDetailResponseDto result = adminReportService.getReportDetail(REPORT_ID);

        // Then
        assertThat(result.getReportId()).isEqualTo(REPORT_ID);
        assertThat(result.getTargetType()).isEqualTo(ReportTargetType.COMMUNITY_POST);
        assertThat(result.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(result.getReason()).isEqualTo(ReportReason.ABUSE);
        assertThat(result.getContent()).isEqualTo("욕설이 포함되어 있습니다");
        assertThat(result.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(result.getReporterName()).isEqualTo("신고자");
        assertThat(result.getReporterNickname()).isEqualTo("신고닉");
        assertThat(result.getReporterEmail()).isEqualTo("reporter@test.com");
        assertThat(result.getReportedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(result.getTarget().getTitle()).isEqualTo("신고 대상 게시글");
        assertThat(result.getTarget().getOwnerAccountId()).isEqualTo(20L);
    }

    @Test
    void 미처리_신고는_관리자_처리내역이_비어있다() {
        // Given
        when(reportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(createReport()));
        when(reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, TARGET_ID))
                .thenReturn(createTargetSnapshot());

        // When
        ReportDetailResponseDto result = adminReportService.getReportDetail(REPORT_ID);

        // Then
        assertThat(result.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(result.getAdminNote()).isNull();
        assertThat(result.getProcessedAt()).isNull();
    }

    @Test
    void 처리된_신고는_관리자_메모와_처리시각이_포함된다() {
        // Given
        Report report = createReport();
        report.review("게시글 삭제 처리함");
        ReflectionTestUtils.setField(report, "modifiedAt", LocalDateTime.now());
        when(reportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, TARGET_ID))
                .thenReturn(createTargetSnapshot());

        // When
        ReportDetailResponseDto result = adminReportService.getReportDetail(REPORT_ID);

        // Then
        assertThat(result.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(result.getAdminNote()).isEqualTo("게시글 삭제 처리함");
        assertThat(result.getProcessedAt()).isNotNull();
    }

    @Test
    void 신고_대상이_삭제됐어도_상세_조회는_성공한다() {
        // Given
        when(reportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(createReport()));
        when(reportTargetResolver.resolve(ReportTargetType.COMMUNITY_POST, TARGET_ID))
                .thenReturn(ReportTargetSnapshotDto.deleted(ReportTargetType.COMMUNITY_POST, TARGET_ID));

        // When
        ReportDetailResponseDto result = adminReportService.getReportDetail(REPORT_ID);

        // Then
        assertThat(result.getReportId()).isEqualTo(REPORT_ID);
        assertThat(result.getTarget().isExists()).isFalse();
    }

    @Test
    void 존재하지_않는_신고를_조회하면_REPORT_NOT_FOUND() {
        // Given
        when(reportRepository.findByReportId(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminReportService.getReportDetail(99L))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
        verify(reportTargetResolver, never()).resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ===================== 신고 처리 =====================

    @Test
    void 신고_검토_처리_시_REVIEWED로_전이된다() {
        // Given
        Report report = createReport();
        ReportReviewRequestDto request = new ReportReviewRequestDto();
        ReflectionTestUtils.setField(request, "adminNote", "조치 완료");
        when(reportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(report));

        // When
        adminReportService.reviewReport(REPORT_ID, request);

        // Then
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(report.getAdminNote()).isEqualTo("조치 완료");
    }
}
