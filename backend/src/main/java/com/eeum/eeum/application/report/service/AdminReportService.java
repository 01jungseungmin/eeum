package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
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

    @Transactional(readOnly = true)
    public Page<ReportResponseDto> getReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByStatus(status, pageable).map(ReportResponseDto::from);
        }
        return reportRepository.findAll(pageable).map(ReportResponseDto::from);
    }

    @Transactional(readOnly = true)
    public ReportResponseDto getReportDetail(Long reportId) {
        return ReportResponseDto.from(getReportOrThrow(reportId));
    }

    @Transactional
    public ReportResponseDto reviewReport(Long reportId, ReportReviewRequestDto request) {
        Report report = getReportOrThrow(reportId);
        report.review(request.getAdminNote());
        return ReportResponseDto.from(report);
    }

    @Transactional
    public ReportResponseDto dismissReport(Long reportId, ReportReviewRequestDto request) {
        Report report = getReportOrThrow(reportId);
        report.dismiss(request.getAdminNote());
        return ReportResponseDto.from(report);
    }

    // ===================== 내부 유틸 =====================

    private Report getReportOrThrow(Long reportId) {
        return reportRepository.findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }
}
