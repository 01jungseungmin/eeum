package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.application.operation.dto.response.OperationFailureLogResponseDto;
import com.eeum.eeum.application.operation.dto.response.OperationSummaryResponseDto;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 운영 현황 조회.
 *
 * <p>결제 Webhook 실패·환불 실패·스케줄러 실패·미처리 신고·미답변 문의를
 * 한 화면에서 확인하기 위한 읽기 전용 서비스다.
 *
 * <p>재처리(재시도) 기능은 제공하지 않는다 — 환불 중복 집행이나 스케줄러 중복 실행 위험이 있어
 * 건별 멱등성 설계가 선행되어야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminOperationService {

    /** 요약 지표의 기본 집계 구간 (시간). */
    private static final int DEFAULT_SUMMARY_HOURS = 24;

    /** 요약에 함께 내려보내는 최근 실패 목록 크기. */
    private static final int RECENT_FAILURE_LIMIT = 10;

    private final OperationFailureLogRepository operationFailureLogRepository;
    private final ReportRepository reportRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public Page<OperationFailureLogResponseDto> getFailures(
            OperationFailureCategory category,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    ) {
        return operationFailureLogRepository
                .searchFailures(category, from, to, keyword, pageable)
                .map(OperationFailureLogResponseDto::from);
    }

    @Transactional(readOnly = true)
    public OperationSummaryResponseDto getSummary(Integer hours) {
        int windowHours = (hours == null || hours <= 0) ? DEFAULT_SUMMARY_HOURS : hours;
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);

        Map<OperationFailureCategory, Long> byCategory =
                operationFailureLogRepository.countByCategorySince(since);
        long failureCount = byCategory.values().stream().mapToLong(Long::longValue).sum();

        List<OperationFailureLogResponseDto> recentFailures =
                operationFailureLogRepository.findRecentFailures(RECENT_FAILURE_LIMIT).stream()
                        .map(OperationFailureLogResponseDto::from)
                        .toList();

        return OperationSummaryResponseDto.builder()
                .pendingReportCount(reportRepository.countByStatus(ReportStatus.PENDING))
                .pendingInquiryCount(inquiryRepository.countByTargetTypeAndStatus(
                        InquiryTargetType.ADMIN, InquiryStatus.PENDING))
                .failureCount(failureCount)
                .failureCountByCategory(byCategory)
                .recentFailures(recentFailures)
                .failureCountSince(since)
                .build();
    }
}
