package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.event.ReportSubmittedEvent;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final ReportTargetResolver reportTargetResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportResponseDto createReport(Long accountId, ReportCreateRequestDto request) {
        Account reporter = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (reportRepository.existsByReporter_AccountIdAndTargetTypeAndTargetId(
                accountId, request.getTargetType(), request.getTargetId())) {
            throw new ConflictException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        ReportTargetSnapshotDto target = reportTargetResolver.resolveForCreation(
                request.getTargetType(), request.getTargetId());
        if (target.getOwnerAccountId().equals(accountId)) {
            throw new BadRequestException(ErrorCode.REPORT_SELF_NOT_ALLOWED);
        }

        Report report = Report.create(
                reporter,
                request.getTargetType(),
                request.getTargetId(),
                request.getReason(),
                request.getContent(),
                target.getTitle(),
                target.getContent()
        );

        Report saved;
        try {
            saved = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        eventPublisher.publishEvent(new ReportSubmittedEvent(
                saved.getReportId(),
                accountId,
                reporter.getNickname(),
                saved.getTargetType(),
                saved.getTargetId()
        ));

        return ReportResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Slice<ReportResponseDto> getMyReports(Long accountId, Pageable pageable) {
        return reportRepository.findByReporter_AccountId(accountId, pageable)
                .map(ReportResponseDto::from);
    }

    @Transactional(readOnly = true)
    public ReportResponseDto getMyReportDetail(Long accountId, Long reportId) {
        Report report = getReportOrThrow(reportId);
        validateOwner(report, accountId);
        return ReportResponseDto.from(report);
    }

    // ===================== 내부 유틸 =====================

    private Report getReportOrThrow(Long reportId) {
        return reportRepository.findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }

    private void validateOwner(Report report, Long accountId) {
        if (!report.isOwnedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.REPORT_ACCESS_DENIED);
        }
    }
}
