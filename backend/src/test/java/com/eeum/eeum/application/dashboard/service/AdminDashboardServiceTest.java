package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminPendingActionsResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryCategoryCount;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportReasonCount;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.entity.Store;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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

    // ─────────────────── 처리 대기 항목 ───────────────────

    @Test
    void 처리_대기_분류별_건수는_없는_키를_0으로_채우고_합계를_낸다() {
        when(reportRepository.countByReasonForStatus(ReportStatus.PENDING)).thenReturn(List.of(
                new ReportReasonCount(ReportReason.FRAUD, 1L),
                new ReportReasonCount(ReportReason.ABUSE, 2L)));
        when(inquiryRepository.countByCategoryForTargetTypeAndStatus(InquiryTargetType.ADMIN, InquiryStatus.PENDING))
                .thenReturn(List.of(new InquiryCategoryCount(InquiryCategory.PAYMENT, 2L)));

        AdminPendingActionsResponseDto result = adminDashboardService.getPendingActions();

        assertThat(result.getReports().getCount()).isEqualTo(3L);
        assertThat(result.getReports().getCountByReason())
                .hasSize(ReportReason.values().length)
                .containsEntry(ReportReason.FRAUD, 1L)
                .containsEntry(ReportReason.ABUSE, 2L)
                .containsEntry(ReportReason.SPAM, 0L);
        assertThat(result.getInquiries().getCount()).isEqualTo(2L);
        assertThat(result.getInquiries().getCountByCategory())
                .hasSize(InquiryCategory.values().length)
                .containsEntry(InquiryCategory.PAYMENT, 2L)
                .containsEntry(InquiryCategory.ACCOUNT, 0L);
    }

    @Test
    void 사장_승인_대기는_가장_최근_신청_상점명과_대기_시각을_담는다() {
        LocalDateTime oldest = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 9, 19, 9, 0);

        Account latestAccount = mock(Account.class);
        when(latestAccount.getAccountId()).thenReturn(7L);
        OwnerInfo latestInfo = mock(OwnerInfo.class);
        when(latestInfo.getAccount()).thenReturn(latestAccount);
        when(latestInfo.getReviewRequestedAt()).thenReturn(latest);
        OwnerInfo oldestInfo = mock(OwnerInfo.class);
        when(oldestInfo.getReviewRequestedAt()).thenReturn(oldest);
        Store store = mock(Store.class);
        when(store.getName()).thenReturn("수제 떡케이크 공방");

        when(ownerInfoRepository.countByApprovalStatusAndReviewRequestedAtIsNotNull(ApprovalStatus.PENDING))
                .thenReturn(12L);
        when(ownerInfoRepository
                .findFirstByApprovalStatusAndReviewRequestedAtIsNotNullOrderByReviewRequestedAtDesc(ApprovalStatus.PENDING))
                .thenReturn(Optional.of(latestInfo));
        when(ownerInfoRepository
                .findFirstByApprovalStatusAndReviewRequestedAtIsNotNullOrderByReviewRequestedAtAsc(ApprovalStatus.PENDING))
                .thenReturn(Optional.of(oldestInfo));
        when(storeRepository.findByAccount_AccountId(7L)).thenReturn(Optional.of(store));

        AdminPendingActionsResponseDto.OwnerApprovals result =
                adminDashboardService.getPendingActions().getOwnerApprovals();

        assertThat(result.getCount()).isEqualTo(12L);
        assertThat(result.getLatestStoreName()).isEqualTo("수제 떡케이크 공방");
        assertThat(result.getLatestRequestedAt()).isEqualTo(latest);
        assertThat(result.getOldestRequestedAt()).isEqualTo(oldest);
    }

    @Test
    void 대기_건이_없으면_건수는_0이고_시각과_상점명은_null이다() {
        AdminPendingActionsResponseDto result = adminDashboardService.getPendingActions();

        assertThat(result.getOwnerApprovals().getCount()).isZero();
        assertThat(result.getOwnerApprovals().getLatestStoreName()).isNull();
        assertThat(result.getOwnerApprovals().getOldestRequestedAt()).isNull();
        assertThat(result.getReports().getCount()).isZero();
        assertThat(result.getReports().getLatestReportedAt()).isNull();
        assertThat(result.getInquiries().getCount()).isZero();
        assertThat(result.getInquiries().getOldestCreatedAt()).isNull();
    }
}
