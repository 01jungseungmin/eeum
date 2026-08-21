package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.response.MyReportResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.event.ReportSubmittedEvent;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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
    public MyReportResponseDto createReport(Long accountId, ReportCreateRequestDto request) {
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
                target.getContent(),
                target.getOwnerAccountId()
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

        return MyReportResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Slice<MyReportResponseDto> getMyReports(Long accountId, Pageable pageable) {
        // 접수 최신순 고정 + PK tie-break — 관리자 목록과 같은 규칙을 쓴다.
        Pageable fixed = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("reportId")));

        return reportRepository.findByReporter_AccountId(accountId, fixed)
                .map(MyReportResponseDto::from);
    }

    @Transactional(readOnly = true)
    public MyReportResponseDto getMyReportDetail(Long accountId, Long reportId) {
        // 소유자를 조회 조건에 넣는다. 남의 신고에 403을 주면 없는 신고(404)와 구분되어
        // ID를 넣어보는 것만으로 신고의 존재 여부를 알아낼 수 있다.
        Report report = reportRepository.findByReportIdAndReporter_AccountId(reportId, accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
        return MyReportResponseDto.from(report);
    }

    // ===================== 내부 유틸 =====================

}
