package com.eeum.eeum.application.dashboard.service;

import com.eeum.eeum.application.dashboard.dto.response.AdminActivityResponseDto;
import com.eeum.eeum.application.dashboard.enums.DashboardActivityType;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
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
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 대시보드 실시간 활동 피드.
 *
 * 별도 활동 로그 테이블 없이 가입·상점 등록·결제·신고 테이블에서 각각 최근 N건을 읽어
 * 시간순으로 합친다. 각 출처에서 limit건씩 읽으면 합친 뒤 상위 limit건은 항상 정확하다.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardActivityService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    // 요약·가입자 추이의 회원 기준과 같다
    private static final List<AccountRole> MEMBER_ROLES = List.of(AccountRole.ROLE_USER, AccountRole.ROLE_OWNER);
    private static final List<AccountStatus> MEMBER_STATUSES = List.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED);

    // 부분 환불은 거래 자체는 성사된 것이므로 포함한다. 전액 취소·환불·실패는 제외
    private static final List<PaymentStatus> COMPLETED_PAYMENT_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED);

    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final StoreRepository storeRepository;
    private final PaymentRepository paymentRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public List<AdminActivityResponseDto> getRecentActivities(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
        Pageable top = PageRequest.of(0, size);

        List<AdminActivityResponseDto> activities = new ArrayList<>();
        activities.addAll(signups(top));
        activities.addAll(storeRegistrations(size));
        activities.addAll(payments(size));
        activities.addAll(reports(top));

        return activities.stream()
                .sorted(Comparator.comparing(AdminActivityResponseDto::getOccurredAt).reversed())
                .limit(size)
                .toList();
    }

    private List<AdminActivityResponseDto> signups(Pageable top) {
        List<Account> accounts =
                accountRepository.findByRoleInAndStatusInOrderByCreatedAtDesc(MEMBER_ROLES, MEMBER_STATUSES, top);

        // primary_region_id는 region이 아니라 account_region의 ID다
        List<Long> primaryAccountRegionIds = accounts.stream()
                .map(Account::getPrimaryRegionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, AccountRegion> primaryRegions = primaryAccountRegionIds.isEmpty()
                ? Map.of()
                : accountRegionRepository.findByAccountRegionIdIn(primaryAccountRegionIds).stream()
                        .collect(Collectors.toMap(AccountRegion::getAccountRegionId, Function.identity()));

        return accounts.stream()
                .map(account -> {
                    AccountRegion primary = account.getPrimaryRegionId() == null
                            ? null : primaryRegions.get(account.getPrimaryRegionId());
                    // 커뮤니티 공개 판정(PrimaryRegionResolver)과 같이 본인 소유·인증 완료 지역만 인정한다
                    boolean usable = primary != null
                            && primary.isVerified()
                            && account.getAccountId().equals(primary.getAccount().getAccountId());
                    return AdminActivityResponseDto.builder()
                            .type(DashboardActivityType.MEMBER_SIGNUP)
                            .targetId(account.getAccountId())
                            .title("새 회원 가입")
                            .description(usable ? primary.getRegion().getGunGu() : null)
                            .occurredAt(account.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private List<AdminActivityResponseDto> storeRegistrations(int size) {
        return storeRepository.findRecentPubliclyVisible(size).stream()
                .map((Store store) -> AdminActivityResponseDto.builder()
                        .type(DashboardActivityType.STORE_REGISTERED)
                        .targetId(store.getStoreId())
                        .title("신규 가게 등록")
                        .description(store.getName())
                        .occurredAt(store.getCreatedAt())
                        .build())
                .toList();
    }

    private List<AdminActivityResponseDto> payments(int size) {
        return paymentRepository.findRecentPaymentActivities(COMPLETED_PAYMENT_STATUSES, size).stream()
                .map((PaymentActivity payment) -> AdminActivityResponseDto.builder()
                        .type(DashboardActivityType.PAYMENT_COMPLETED)
                        .targetId(payment.paymentId())
                        .title("거래 완료")
                        .description(payment.storeName())
                        .amount(payment.amount())
                        .occurredAt(payment.paidAt())
                        .build())
                .toList();
    }

    private List<AdminActivityResponseDto> reports(Pageable top) {
        return reportRepository.findAllByOrderByCreatedAtDesc(top).stream()
                .map((Report report) -> AdminActivityResponseDto.builder()
                        .type(DashboardActivityType.REPORT_RECEIVED)
                        .targetId(report.getReportId())
                        .title("신고 접수")
                        .description(reasonLabel(report.getReason()) + " · " + targetLabel(report.getTargetType()))
                        .occurredAt(report.getCreatedAt())
                        .build())
                .toList();
    }

    private static String reasonLabel(ReportReason reason) {
        return switch (reason) {
            case SPAM -> "스팸";
            case ABUSE -> "욕설/비방";
            case FRAUD -> "사기 의심";
            case INAPPROPRIATE_CONTENT -> "부적절한 콘텐츠";
            case FALSE_INFORMATION -> "허위 정보";
            case PERSONAL_INFORMATION -> "개인정보 노출";
            case ETC -> "기타";
        };
    }

    private static String targetLabel(ReportTargetType targetType) {
        return switch (targetType) {
            case STORE -> "가게";
            case STORE_REVIEW -> "가게 리뷰";
            case COMMUNITY_POST -> "커뮤니티 게시글";
            case COMMUNITY_COMMENT -> "커뮤니티 댓글";
            case ACCOUNT -> "회원";
            case USED_PRODUCT -> "중고거래 게시글";
        };
    }
}
