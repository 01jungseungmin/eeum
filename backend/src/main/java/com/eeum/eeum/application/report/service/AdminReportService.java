package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final ReportTargetResolver reportTargetResolver;

    @Transactional(readOnly = true)
    public Page<ReportResponseDto> getReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByStatus(status, pageable).map(ReportResponseDto::from);
        }
        return reportRepository.findAll(pageable).map(ReportResponseDto::from);
    }

    // 신고 상세 — 신고자 정보 + 대상 콘텐츠 스냅샷 + 관리자 처리 내역
    // 쿼리 2회 고정 (신고+신고자 fetch join 1회, 대상 fetch join 1회)
    @Transactional(readOnly = true)
    public ReportResponseDto getReportDetail(Long reportId) {
        Report report = getReportOrThrow(reportId);
        ReportTargetSnapshotDto currentTarget =
                reportTargetResolver.resolve(report.getTargetType(), report.getTargetId());
        ReportTargetSnapshotDto target = ReportTargetSnapshotDto.withStoredContent(
                currentTarget,
                report.getTargetTitleSnapshot(),
                report.getTargetContentSnapshot());
        return ReportResponseDto.of(report, target);
    }

    @Transactional
    public ReportResponseDto reviewReport(Long reportId, ReportReviewRequestDto request) {
        Report report = getReportOrThrow(reportId);
        report.review(request.getAdminNote());
        // modifiedAt은 JPA flush 시점에 갱신된다. flush 전에 DTO를 만들면 처리 응답의
        // updatedAt/processedAt이 신고 접수 시각으로 남으므로, 갱신 완료 후 응답을 변환한다.
        Report saved = reportRepository.saveAndFlush(report);
        return ReportResponseDto.from(saved);
    }

    @Transactional
    public ReportResponseDto dismissReport(Long reportId, ReportReviewRequestDto request) {
        Report report = getReportOrThrow(reportId);
        report.dismiss(request.getAdminNote());
        Report saved = reportRepository.saveAndFlush(report);
        return ReportResponseDto.from(saved);
    }

    // ===================== 내부 유틸 =====================

    private Report getReportOrThrow(Long reportId) {
        return reportRepository.findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }
}
