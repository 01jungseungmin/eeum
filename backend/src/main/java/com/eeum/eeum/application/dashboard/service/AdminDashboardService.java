package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminPendingActionsResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminRegionMemberResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminSignupTrendResponseDto;
import com.eeum.eeum.application.dashboard.enums.SignupMemberType;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.repository.InquiryCategoryCount;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import com.eeum.eeum.domain.report.repository.ReportReasonCount;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;

/**
 * 관리자 대시보드 집계 — 상단 KPI, 처리 대기 항목, 가입자 추이, 지역별 회원.
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

    private static final int DEFAULT_SIGNUP_DAYS = 7;

    // 가입 시각을 모두 읽어 애플리케이션에서 일자별로 묶으므로 조회 기간에 상한을 둔다
    private static final int MAX_SIGNUP_DAYS = 90;

    private static final int DEFAULT_REGION_LIMIT = 6;
    private static final int MAX_REGION_LIMIT = 50;

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

    @Transactional(readOnly = true)
    public AdminPendingActionsResponseDto getPendingActions() {
        return AdminPendingActionsResponseDto.builder()
                .ownerApprovals(pendingOwnerApprovals())
                .reports(pendingReports())
                .inquiries(pendingInquiries())
                .build();
    }

    private AdminPendingActionsResponseDto.OwnerApprovals pendingOwnerApprovals() {
        Optional<OwnerInfo> latest = ownerInfoRepository
                .findFirstByApprovalStatusAndReviewRequestedAtIsNotNullOrderByReviewRequestedAtDesc(ApprovalStatus.PENDING);

        // 상점은 사장 가입 시 함께 만들어지지만, 없는 데이터가 섞여 있어도 대시보드는 떠야 한다
        String latestStoreName = latest
                .flatMap(info -> storeRepository.findByAccount_AccountId(info.getAccount().getAccountId()))
                .map(Store::getName)
                .orElse(null);

        return AdminPendingActionsResponseDto.OwnerApprovals.builder()
                .count(ownerInfoRepository.countByApprovalStatusAndReviewRequestedAtIsNotNull(ApprovalStatus.PENDING))
                .latestStoreName(latestStoreName)
                .latestRequestedAt(latest.map(OwnerInfo::getReviewRequestedAt).orElse(null))
                .oldestRequestedAt(ownerInfoRepository
                        .findFirstByApprovalStatusAndReviewRequestedAtIsNotNullOrderByReviewRequestedAtAsc(ApprovalStatus.PENDING)
                        .map(OwnerInfo::getReviewRequestedAt)
                        .orElse(null))
                .build();
    }

    private AdminPendingActionsResponseDto.Reports pendingReports() {
        Map<ReportReason, Long> countByReason = zeroFilled(ReportReason.class);
        for (ReportReasonCount row : reportRepository.countByReasonForStatus(ReportStatus.PENDING)) {
            countByReason.put(row.reason(), row.count());
        }

        return AdminPendingActionsResponseDto.Reports.builder()
                .count(sum(countByReason))
                .countByReason(countByReason)
                .latestReportedAt(reportRepository.findFirstByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                        .map(Report::getCreatedAt).orElse(null))
                .oldestReportedAt(reportRepository.findFirstByStatusOrderByCreatedAtAsc(ReportStatus.PENDING)
                        .map(Report::getCreatedAt).orElse(null))
                .build();
    }

    private AdminPendingActionsResponseDto.Inquiries pendingInquiries() {
        Map<InquiryCategory, Long> countByCategory = zeroFilled(InquiryCategory.class);
        for (InquiryCategoryCount row : inquiryRepository.countByCategoryForTargetTypeAndStatus(
                InquiryTargetType.ADMIN, InquiryStatus.PENDING)) {
            countByCategory.put(row.category(), row.count());
        }

        return AdminPendingActionsResponseDto.Inquiries.builder()
                .count(sum(countByCategory))
                .countByCategory(countByCategory)
                .latestCreatedAt(inquiryRepository
                        .findFirstByTargetTypeAndStatusOrderByCreatedAtDesc(InquiryTargetType.ADMIN, InquiryStatus.PENDING)
                        .map(Inquiry::getCreatedAt).orElse(null))
                .oldestCreatedAt(inquiryRepository
                        .findFirstByTargetTypeAndStatusOrderByCreatedAtAsc(InquiryTargetType.ADMIN, InquiryStatus.PENDING)
                        .map(Inquiry::getCreatedAt).orElse(null))
                .build();
    }

    // 없는 키를 0으로 채워 응답 형태를 고정한다 — 키가 빠지면 프론트가 매번 존재 여부를 확인해야 한다
    private static <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            result.put(value, 0L);
        }
        return result;
    }

    private static long sum(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 오늘을 포함한 최근 days일의 일자별 가입자 수.
     * 회원 기준은 요약의 전체 회원과 같다(관리자·탈퇴·가입 미완료 제외).
     */
    @Transactional(readOnly = true)
    public AdminSignupTrendResponseDto getSignupTrend(SignupMemberType type, Integer days) {
        int period = days == null ? DEFAULT_SIGNUP_DAYS : days;
        if (period < 1 || period > MAX_SIGNUP_DAYS) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
        SignupMemberType memberType = type == null ? SignupMemberType.GENERAL : type;

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period - 1L);

        Map<LocalDate, Long> countByDate = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            countByDate.put(date, 0L);
        }
        List<LocalDateTime> signupTimes = accountRepository.findSignupTimes(
                memberType == SignupMemberType.OWNER, MEMBER_ROLES, MEMBER_STATUSES,
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        for (LocalDateTime signupTime : signupTimes) {
            countByDate.merge(signupTime.toLocalDate(), 1L, Long::sum);
        }

        List<AdminSignupTrendResponseDto.Daily> daily = new ArrayList<>(countByDate.size());
        countByDate.forEach((date, count) -> daily.add(AdminSignupTrendResponseDto.Daily.builder()
                .date(date)
                .dayOfWeek(date.getDayOfWeek())
                .count(count)
                .build()));

        return AdminSignupTrendResponseDto.builder()
                .type(memberType)
                .from(from)
                .to(to)
                .total(signupTimes.size())
                .daily(daily)
                .build();
    }

    /**
     * 대표 동네 기준 구·군별 회원 수 상위 limit개.
     * 동네 인증을 마친 회원만 센다 — 가입만 하고 동네 활동을 시작하지 않은 계정은 제외된다.
     */
    @Transactional(readOnly = true)
    public List<AdminRegionMemberResponseDto> getRegionMembers(Integer limit) {
        int size = limit == null ? DEFAULT_REGION_LIMIT : limit;
        if (size < 1 || size > MAX_REGION_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        return accountRepository.countVerifiedMembersByRegion(MEMBER_ROLES, MEMBER_STATUSES, size).stream()
                .map(AdminRegionMemberResponseDto::from)
                .toList();
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
