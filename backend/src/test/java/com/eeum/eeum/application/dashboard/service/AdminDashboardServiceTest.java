package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OwnerInfoRepository ownerInfoRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private InquiryRepository inquiryRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    void 처리_대기는_사장_승인_신고_관리자_문의의_합이다() {
        when(ownerInfoRepository.countByApprovalStatusAndReviewRequestedAtIsNotNull(ApprovalStatus.PENDING))
                .thenReturn(12L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(5L);
        when(inquiryRepository.countByTargetTypeAndStatus(InquiryTargetType.ADMIN, InquiryStatus.PENDING))
                .thenReturn(6L);

        AdminDashboardSummaryResponseDto result = adminDashboardService.getSummary();

        assertThat(result.getPendingOwnerApprovals()).isEqualTo(12L);
        assertThat(result.getPendingReports()).isEqualTo(5L);
        assertThat(result.getPendingInquiries()).isEqualTo(6L);
        assertThat(result.getPendingTotal()).isEqualTo(23L);
    }

    @Test
    void 신규_집계는_이번_주_월요일_자정부터_센다() {
        when(storeRepository.countPubliclyVisible(isNull())).thenReturn(438L);
        when(storeRepository.countPubliclyVisible(any(LocalDateTime.class))).thenReturn(12L);

        AdminDashboardSummaryResponseDto result = adminDashboardService.getSummary();

        ArgumentCaptor<LocalDateTime> weekStart = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(accountRepository).countByRoleInAndStatusInAndCreatedAtGreaterThanEqual(
                anyCollection(), anyCollection(), weekStart.capture());
        assertThat(weekStart.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(weekStart.getValue().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);

        assertThat(result.getActiveStores()).isEqualTo(438L);
        assertThat(result.getNewStoresThisWeek()).isEqualTo(12L);
    }

    @Test
    void 어제_거래는_오늘과_같은_시간_구간으로_센다() {
        adminDashboardService.getSummary();

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, times(2))
                .countByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        anyCollection(), from.capture(), to.capture());

        LocalDateTime todayStart = from.getAllValues().get(0);
        LocalDateTime now = to.getAllValues().get(0);
        assertThat(todayStart.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(from.getAllValues().get(1)).isEqualTo(todayStart.minusDays(1));
        assertThat(to.getAllValues().get(1)).isEqualTo(now.minusDays(1));
    }

    @Test
    void 전일_대비_증감률은_소수_첫째_자리로_반올림한다() {
        assertThat(AdminDashboardService.changeRate(1284, 1187)).isEqualByComparingTo("8.2");
        assertThat(AdminDashboardService.changeRate(90, 100)).isEqualByComparingTo("-10.0");
    }

    @Test
    void 어제_거래가_0건이면_증감률은_null이다() {
        assertThat(AdminDashboardService.changeRate(5, 0)).isNull();
    }
}
