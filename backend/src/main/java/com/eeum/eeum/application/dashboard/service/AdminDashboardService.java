package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 관리자 대시보드 상단 KPI 집계.
 *
 * 운영 실패·신고·문의 중심의 AdminOperationService.getSummary와 달리
 * 회원·사업장·거래 규모와 처리 대기 건수를 한 번에 내려준다.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final List<AccountRole> MEMBER_ROLES = List.of(AccountRole.ROLE_USER, AccountRole.ROLE_OWNER);

    // 탈퇴·가입 미완료(PENDING) 계정은 회원으로 세지 않는다. 정지 계정은 회원 자격이 남아 있으므로 포함한다
    private static final List<AccountStatus> MEMBER_STATUSES = List.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED);

    // 결제·확정 이후 단계만 거래로 본다. PENDING은 결제 전, CANCELLED·EXPIRED는 성사되지 않은 주문이다
    private static final List<OrderStatus> TRANSACTION_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.COMPLETED);

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final ReportRepository reportRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponseDto getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = todayStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        long todayOrders = orderRepository.countByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                TRANSACTION_STATUSES, todayStart, now);
        long yesterdayOrders = orderRepository.countByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                TRANSACTION_STATUSES, todayStart.minusDays(1), now.minusDays(1));

        long pendingOwnerApprovals =
                ownerInfoRepository.countByApprovalStatusAndReviewRequestedAtIsNotNull(ApprovalStatus.PENDING);
        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);
        long pendingInquiries =
                inquiryRepository.countByTargetTypeAndStatus(InquiryTargetType.ADMIN, InquiryStatus.PENDING);

        return AdminDashboardSummaryResponseDto.builder()
                .totalMembers(accountRepository.countByRoleInAndStatusIn(MEMBER_ROLES, MEMBER_STATUSES))
                .newMembersThisWeek(accountRepository.countByRoleInAndStatusInAndCreatedAtGreaterThanEqual(
                        MEMBER_ROLES, MEMBER_STATUSES, weekStart))
                .activeStores(storeRepository.countPubliclyVisible(null))
                .newStoresThisWeek(storeRepository.countPubliclyVisible(weekStart))
                .todayOrders(todayOrders)
                .yesterdayOrders(yesterdayOrders)
                .orderChangeRate(changeRate(todayOrders, yesterdayOrders))
                .pendingTotal(pendingOwnerApprovals + pendingReports + pendingInquiries)
                .pendingOwnerApprovals(pendingOwnerApprovals)
                .pendingReports(pendingReports)
                .pendingInquiries(pendingInquiries)
                .aggregatedAt(now)
                .build();
    }

    static BigDecimal changeRate(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }
}
