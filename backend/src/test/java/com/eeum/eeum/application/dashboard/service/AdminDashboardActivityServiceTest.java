package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminActivityResponseDto;
import com.eeum.eeum.application.dashboard.enums.DashboardActivityType;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.PaymentActivity;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.report.entity.Report;
import com.eeum.eeum.domain.report.enums.ReportReason;
import com.eeum.eeum.domain.report.enums.ReportTargetType;
import com.eeum.eeum.domain.report.repository.ReportRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardActivityServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 19, 12, 0);

    @Mock private AccountRepository accountRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReportRepository reportRepository;

    @InjectMocks
    private AdminDashboardActivityService adminDashboardActivityService;

    @Test
    void 출처별_최근_활동을_최신순으로_합쳐_limit건만_반환한다() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(1L);
        when(account.getPrimaryRegionId()).thenReturn(100L);
        when(account.getCreatedAt()).thenReturn(BASE);
        when(accountRepository.findByRoleInAndStatusInOrderByCreatedAtDesc(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(account));
        AccountRegion primary = accountRegion(100L, account, true, "강남구");
        when(accountRegionRepository.findAllWithRegionByIdIn(List.of(100L))).thenReturn(List.of(primary));

        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(2L);
        when(store.getName()).thenReturn("온담 커피");
        when(store.getCreatedAt()).thenReturn(BASE.minusMinutes(5));
        when(storeRepository.findRecentPubliclyVisible(3)).thenReturn(List.of(store));

        when(paymentRepository.findRecentPaymentActivities(
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED), PageRequest.of(0, 3)))
                .thenReturn(List.of(
                new PaymentActivity(3L, "오늘 반찬", new BigDecimal("47500"), BASE.minusMinutes(8))));

        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(4L);
        when(report.getReason()).thenReturn(ReportReason.FRAUD);
        when(report.getTargetType()).thenReturn(ReportTargetType.USED_PRODUCT);
        when(report.getCreatedAt()).thenReturn(BASE.minusMinutes(12));
        when(reportRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(report));

        List<AdminActivityResponseDto> result = adminDashboardActivityService.getRecentActivities(3);

        assertThat(result)
                .extracting(AdminActivityResponseDto::getType, AdminActivityResponseDto::getTargetId,
                        AdminActivityResponseDto::getDescription)
                .containsExactly(
                        tuple(DashboardActivityType.MEMBER_SIGNUP, 1L, "강남구"),
                        tuple(DashboardActivityType.STORE_REGISTERED, 2L, "온담 커피"),
                        tuple(DashboardActivityType.PAYMENT_COMPLETED, 3L, "오늘 반찬"));
        assertThat(result.get(2).getAmount()).isEqualByComparingTo("47500");
    }

    @Test
    void 신고는_사유와_대상을_설명으로_담는다() {
        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(4L);
        when(report.getReason()).thenReturn(ReportReason.ABUSE);
        when(report.getTargetType()).thenReturn(ReportTargetType.COMMUNITY_POST);
        when(report.getCreatedAt()).thenReturn(BASE);
        when(reportRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(report));

        List<AdminActivityResponseDto> result = adminDashboardActivityService.getRecentActivities(null);

        assertThat(result).singleElement()
                .extracting(AdminActivityResponseDto::getTitle, AdminActivityResponseDto::getDescription)
                .containsExactly("신고 접수", "욕설/비방 · 커뮤니티 게시글");
    }

    @Test
    void 대표_동네가_없는_가입자는_설명이_null이다() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(1L);
        when(account.getCreatedAt()).thenReturn(BASE);
        when(accountRepository.findByRoleInAndStatusInOrderByCreatedAtDesc(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(account));

        List<AdminActivityResponseDto> result = adminDashboardActivityService.getRecentActivities(null);

        assertThat(result).singleElement()
                .extracting(AdminActivityResponseDto::getDescription)
                .isNull();
    }

    @Test
    void 조회_건수가_범위를_벗어나면_예외가_발생한다() {
        assertThatThrownBy(() -> adminDashboardActivityService.getRecentActivities(0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adminDashboardActivityService.getRecentActivities(51))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 대표_동네가_인증되지_않았으면_설명이_null이다() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(1L);
        when(account.getPrimaryRegionId()).thenReturn(100L);
        when(account.getCreatedAt()).thenReturn(BASE);
        when(accountRepository.findByRoleInAndStatusInOrderByCreatedAtDesc(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(account));
        AccountRegion unverified = mock(AccountRegion.class);
        when(unverified.getAccountRegionId()).thenReturn(100L);
        when(unverified.isVerified()).thenReturn(false);
        when(accountRegionRepository.findAllWithRegionByIdIn(List.of(100L))).thenReturn(List.of(unverified));

        List<AdminActivityResponseDto> result = adminDashboardActivityService.getRecentActivities(null);

        assertThat(result).singleElement()
                .extracting(AdminActivityResponseDto::getDescription)
                .isNull();
    }

    private AccountRegion accountRegion(Long accountRegionId, Account owner, boolean verified, String gunGu) {
        Region region = mock(Region.class);
        when(region.getGunGu()).thenReturn(gunGu);
        AccountRegion accountRegion = mock(AccountRegion.class);
        when(accountRegion.getAccountRegionId()).thenReturn(accountRegionId);
        when(accountRegion.isVerified()).thenReturn(verified);
        when(accountRegion.getAccount()).thenReturn(owner);
        when(accountRegion.getRegion()).thenReturn(region);
        return accountRegion;
    }
}
