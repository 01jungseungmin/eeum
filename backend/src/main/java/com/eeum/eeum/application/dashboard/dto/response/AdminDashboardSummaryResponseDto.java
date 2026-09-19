package com.eeum.eeum.application.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "관리자 대시보드 상단 KPI 요약")
public class AdminDashboardSummaryResponseDto {

    @Schema(description = "전체 회원 수 (일반·사장, 활성·정지 상태). 관리자·탈퇴·가입 미완료 계정 제외", example = "12847")
    private final long totalMembers;

    @Schema(description = "이번 주(월요일 00:00 이후) 신규 회원 수. 전체 회원과 같은 기준", example = "182")
    private final long newMembersThisWeek;

    @Schema(description = "활성 사업장 수. 사용자에게 공개 노출되는 상점(사장 승인 완료·계정 활성·상점 미정지)", example = "438")
    private final long activeStores;

    @Schema(description = "이번 주(월요일 00:00 이후) 생성된 활성 사업장 수", example = "12")
    private final long newStoresThisWeek;

    @Schema(description = "오늘(00:00 ~ 현재) 거래 건수. 결제 완료 이후 상태(PAID·CONFIRMED·READY·COMPLETED) 주문", example = "1284")
    private final long todayOrders;

    @Schema(description = "어제 같은 시각까지(어제 00:00 ~ 현재 시각 - 1일)의 거래 건수. 오늘과 같은 시간 구간으로 비교하기 위함", example = "1187")
    private final long yesterdayOrders;

    @Schema(description = "전일 대비 거래 증감률(%). 소수 첫째 자리 반올림. 어제 거래가 0건이면 null", example = "8.2", nullable = true)
    private final BigDecimal orderChangeRate;

    @Schema(description = "처리 대기 합계 (사장 승인 + 신고 + 관리자 문의)", example = "23")
    private final long pendingTotal;

    @Schema(description = "심사 요청된 사장 가입 승인 대기 건수", example = "12")
    private final long pendingOwnerApprovals;

    @Schema(description = "미처리(PENDING) 신고 건수", example = "5")
    private final long pendingReports;

    @Schema(description = "미답변(PENDING) 관리자 문의 건수", example = "6")
    private final long pendingInquiries;

    @Schema(description = "집계 기준 시각")
    private final LocalDateTime aggregatedAt;
}
