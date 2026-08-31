package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.application.operation.dto.response.OperationFailureLogResponseDto;
import com.eeum.eeum.application.operation.dto.response.OperationSummaryResponseDto;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationServiceTest {

    @Mock private OperationFailureLogRepository operationFailureLogRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private InquiryRepository inquiryRepository;

    @InjectMocks
    private AdminOperationService adminOperationService;

    // ─────────────────── 실패 이력 목록 ───────────────────

    @Test
    void 필터를_주지_않으면_전체_실패_이력을_조회한다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        when(operationFailureLogRepository.searchFailures(
                isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(failureLog(
                        1L, OperationFailureCategory.REFUND, "PaymentService.cancelPayment"))));

        // when
        var result = adminOperationService.getFailures(null, null, null, null, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        OperationFailureLogResponseDto dto = result.getContent().get(0);
        assertThat(dto.getCategory()).isEqualTo(OperationFailureCategory.REFUND);
        assertThat(dto.getOperation()).isEqualTo("PaymentService.cancelPayment");
    }

    @Test
    void 카테고리와_기간_필터가_리포지토리로_그대로_전달된다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 31, 23, 59);
        when(operationFailureLogRepository.searchFailures(
                any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        adminOperationService.getFailures(
                OperationFailureCategory.PAYMENT_WEBHOOK, from, to, "portone", pageable);

        // then: 조건을 서비스가 임의로 바꾸지 않는다
        ArgumentCaptor<OperationFailureCategory> categoryCaptor =
                ArgumentCaptor.forClass(OperationFailureCategory.class);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(operationFailureLogRepository).searchFailures(
                categoryCaptor.capture(), fromCaptor.capture(), toCaptor.capture(),
                keywordCaptor.capture(), eq(pageable));

        assertThat(categoryCaptor.getValue()).isEqualTo(OperationFailureCategory.PAYMENT_WEBHOOK);
        assertThat(fromCaptor.getValue()).isEqualTo(from);
        assertThat(toCaptor.getValue()).isEqualTo(to);
        assertThat(keywordCaptor.getValue()).isEqualTo("portone");
    }

    // ─────────────────── 요약 ───────────────────

    @Test
    void 요약은_미처리_신고와_미답변_문의와_카테고리별_실패를_함께_반환한다() {
        // given
        stubSummary(Map.of(
                OperationFailureCategory.REFUND, 3L,
                OperationFailureCategory.SCHEDULER, 2L));
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(7L);
        when(inquiryRepository.countByTargetTypeAndStatus(
                InquiryTargetType.ADMIN, InquiryStatus.PENDING)).thenReturn(4L);

        // when
        OperationSummaryResponseDto summary = adminOperationService.getSummary(24);

        // then
        assertThat(summary.getPendingReportCount()).isEqualTo(7L);
        assertThat(summary.getPendingInquiryCount()).isEqualTo(4L);
        assertThat(summary.getFailureCount())
                .as("전체 실패 건수는 카테고리별 합계와 일치해야 한다")
                .isEqualTo(5L);
    }

    @Test
    void 발생하지_않은_카테고리도_0으로_채워_응답_형태를_고정한다() {
        // given: REFUND만 발생한 상황
        stubSummary(Map.of(OperationFailureCategory.REFUND, 1L));
        when(reportRepository.countByStatus(any())).thenReturn(0L);
        when(inquiryRepository.countByTargetTypeAndStatus(any(), any())).thenReturn(0L);

        // when
        OperationSummaryResponseDto summary = adminOperationService.getSummary(null);

        // then: 프론트가 키 존재 여부를 확인하지 않아도 되도록 전 카테고리가 있어야 한다
        assertThat(summary.getFailureCountByCategory())
                .containsOnlyKeys(OperationFailureCategory.values());
        assertThat(summary.getFailureCountByCategory())
                .containsEntry(OperationFailureCategory.PAYMENT_WEBHOOK, 0L)
                .containsEntry(OperationFailureCategory.REFUND, 1L);
    }

    @Test
    void hours가_null이거나_0이하면_기본_24시간_구간으로_집계한다() {
        // given
        stubSummary(Map.of());
        when(reportRepository.countByStatus(any())).thenReturn(0L);
        when(inquiryRepository.countByTargetTypeAndStatus(any(), any())).thenReturn(0L);

        LocalDateTime beforeCall = LocalDateTime.now().minusHours(24);

        // when
        OperationSummaryResponseDto summary = adminOperationService.getSummary(0);

        // then: 0을 그대로 쓰면 집계 구간이 사라져 항상 0건이 된다
        assertThat(summary.getFailureCountSince())
                .isAfterOrEqualTo(beforeCall.minusMinutes(1))
                .isBefore(LocalDateTime.now());
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void stubSummary(Map<OperationFailureCategory, Long> counts) {
        Map<OperationFailureCategory, Long> filled =
                new EnumMap<>(OperationFailureCategory.class);
        for (OperationFailureCategory category : OperationFailureCategory.values()) {
            filled.put(category, counts.getOrDefault(category, 0L));
        }
        when(operationFailureLogRepository.countByCategorySince(any(LocalDateTime.class)))
                .thenReturn(filled);
        when(operationFailureLogRepository.findRecentFailures(anyInt()))
                .thenReturn(List.of());
    }

    private OperationFailureLog failureLog(
            Long id, OperationFailureCategory category, String operation) {
        OperationFailureLog log = OperationFailureLog.create(
                category, operation, "PAYMENT", "42",
                "PAYMENT_REFUND_FAILED", "PortOne 취소 실패", "amount=10000");
        ReflectionTestUtils.setField(log, "operationFailureLogId", id);
        ReflectionTestUtils.setField(log, "createdAt", LocalDateTime.now());
        return log;
    }
}
