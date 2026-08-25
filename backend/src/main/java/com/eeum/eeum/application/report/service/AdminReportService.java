package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.report.dto.request.ReportProcessRequestDto;
import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportTargetSnapshotDto;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final ReportTargetResolver reportTargetResolver;
    private final ReportActionDispatcher reportActionDispatcher;
    private final SanctionHistoryService sanctionHistoryService;

    @Transactional(readOnly = true)
    public Page<ReportResponseDto> getReports(ReportStatus status, Pageable pageable) {
        // 정렬은 접수 최신순으로 고정한다. 요청 sort를 그대로 쓰면 허용하지 않는 필드가
        // 넘어왔을 때 실제 순서와 응답 메타데이터가 어긋난다.
        Pageable fixed = latestFirst(pageable);

        if (status != null) {
            return reportRepository.findByStatus(status, fixed).map(ReportResponseDto::from);
        }
        return reportRepository.findAll(fixed).map(ReportResponseDto::from);
    }

    // 접수 최신순 + PK tie-break. createdAt 동률 시 페이지 경계에서 항목이 중복·유실된다.
    private Pageable latestFirst(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("reportId")));
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
                report.getTargetContentSnapshot(),
                report.getTargetOwnerAccountIdSnapshot());
        return ReportResponseDto.of(report, target);
    }

    @Transactional
    public ReportResponseDto reviewReport(Long reportId, ReportReviewRequestDto request) {
        return reviewReport(reportId, null, request);
    }

    @Transactional
    public ReportResponseDto reviewReport(
            Long reportId,
            Long adminId,
            ReportReviewRequestDto request
    ) {
        Report report = getReportForUpdateOrThrow(reportId);
        report.review(adminId, request.getAdminNote());
        // modifiedAt은 JPA flush 시점에 갱신된다. flush 전에 DTO를 만들면 처리 응답의
        // updatedAt/processedAt이 신고 접수 시각으로 남으므로, 갱신 완료 후 응답을 변환한다.
        Report saved = reportRepository.saveAndFlush(report);
        return ReportResponseDto.from(saved);
    }

    @Transactional
    public ReportResponseDto dismissReport(Long reportId, ReportReviewRequestDto request) {
        return dismissReport(reportId, null, request);
    }

    @Transactional
    public ReportResponseDto dismissReport(
            Long reportId,
            Long adminId,
            ReportReviewRequestDto request
    ) {
        Report report = getReportForUpdateOrThrow(reportId);
        report.dismiss(adminId, request.getAdminNote());
        Report saved = reportRepository.saveAndFlush(report);
        return ReportResponseDto.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportResponseDto processReport(
            Long reportId,
            Long adminId,
            ReportProcessRequestDto request
    ) {
        Report report = getReportForUpdateOrThrow(reportId);
        report.validateProcessable();
        ReportAction action = request.getAction();

        Long actionTargetAccountId = null;
        if (action != ReportAction.DISMISS) {
            actionTargetAccountId = reportActionDispatcher.execute(
                    report.getTargetType(),
                    action,
                    report.getTargetId(),
                    report.getTargetOwnerAccountIdSnapshot(),
                    request.getAdminNote()
            );
            recordSanctionHistory(
                    report,
                    action,
                    actionTargetAccountId,
                    adminId,
                    request.getAdminNote()
            );
        }

        report.process(action, request.getAdminNote(), adminId, actionTargetAccountId);
        Report saved = reportRepository.saveAndFlush(report);
        return ReportResponseDto.from(saved);
    }

    // ===================== 내부 유틸 =====================

    private Report getReportOrThrow(Long reportId) {
        return reportRepository.findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }

    private Report getReportForUpdateOrThrow(Long reportId) {
        return reportRepository.findByReportIdForUpdate(reportId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }

    private void recordSanctionHistory(
            Report report,
            ReportAction action,
            Long actionTargetAccountId,
            Long adminId,
            String adminNote
    ) {
        switch (action) {
            case WARN_AUTHOR -> sanctionHistoryService.recordReportAction(
                    SanctionTargetType.ACCOUNT,
                    actionTargetAccountId,
                    SanctionAction.WARN,
                    adminNote,
                    adminId,
                    report.getReportId()
            );
            case SUSPEND_AUTHOR -> sanctionHistoryService.recordReportAction(
                    SanctionTargetType.ACCOUNT,
                    actionTargetAccountId,
                    SanctionAction.SUSPEND,
                    adminNote,
                    adminId,
                    report.getReportId()
            );
            case SUSPEND_STORE -> sanctionHistoryService.recordReportAction(
                    SanctionTargetType.STORE,
                    report.getTargetId(),
                    SanctionAction.SUSPEND,
                    adminNote,
                    adminId,
                    report.getReportId()
            );
            default -> {
            }
        }
    }
}
