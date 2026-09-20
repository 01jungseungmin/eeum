package com.eeum.eeum.api.dashboard;

import com.eeum.eeum.application.dashboard.dto.response.AdminActivityResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminPendingActionsResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminRegionMemberResponseDto;
import com.eeum.eeum.application.dashboard.dto.response.AdminSignupTrendResponseDto;
import com.eeum.eeum.application.dashboard.enums.SignupMemberType;
import com.eeum.eeum.application.dashboard.service.AdminDashboardActivityService;
import com.eeum.eeum.application.dashboard.service.AdminDashboardService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "35. Admin - Dashboard", description = "관리자 대시보드 지표")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminDashboardActivityService adminDashboardActivityService;

    @Operation(
            summary = "대시보드 KPI 요약",
            description = """
                    관리자 대시보드 상단 카드(전체 회원·활성 사업장·오늘 거래·처리 대기) 지표를 한 번에 반환합니다.

                    **집계 기준**
                    - 회원: 일반·사장 계정 중 활성·정지 상태. 관리자·탈퇴·가입 미완료 제외
                    - 활성 사업장: 사용자에게 공개 노출되는 상점(사장 승인 완료·계정 활성·상점 미정지)
                    - "이번 주"는 월요일 00:00부터 현재까지입니다
                    - 신규 회원은 계정 생성 시각, 신규 사업장은 상점 생성 시각(사장 가입 시점) 기준입니다. 승인 시각은 기록되지 않아 쓰지 않습니다
                    - 거래: 해당 기간에 생성된 주문 중 현재 결제 완료 이후 상태(PAID·CONFIRMED·READY·COMPLETED)인 주문. 결제 시각이 아니라 주문 생성 시각 기준입니다
                    - 전일 대비는 어제 같은 시각까지의 거래와 비교합니다. 어제 거래가 0건이면 `orderChangeRate`는 null입니다
                    - 처리 대기: 심사 요청된 사장 승인 대기 + 미처리 신고 + 미답변 관리자 문의
                    """
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryResponseDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary()));
    }

    @Operation(
            summary = "대시보드 처리 대기 항목",
            description = """
                    관리자가 처리해야 할 대기 건을 항목별 건수·세부 분류·대기 시각과 함께 반환합니다.

                    - 사장 가입 승인: 심사 요청된 승인 대기 건. 가장 최근 신청 상점명 포함
                    - 신고: 미처리(PENDING) 신고. 사유별 건수(`countByReason`)
                    - 문의: 미답변(PENDING) 관리자 문의. 유형별 건수(`countByCategory`)
                    - 분류별 건수 맵은 발생하지 않은 키도 0으로 채워 항상 같은 형태로 내려갑니다
                    - `oldest*` 시각으로 오래 방치된 항목을 긴급으로 표시할 수 있습니다. 대기 건이 없으면 시각 필드는 null입니다
                    - 건수는 `GET /admin/dashboard/summary`의 pending* 값과 같은 기준입니다
                    """
    )
    @GetMapping("/pending-actions")
    public ResponseEntity<ApiResponse<AdminPendingActionsResponseDto>> getPendingActions() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getPendingActions()));
    }

    @Operation(
            summary = "대시보드 가입자 추이",
            description = """
                    오늘을 포함한 최근 N일의 일자별 가입자 수를 반환합니다. 가입자가 없는 날도 0으로 포함됩니다.

                    - `type=GENERAL`: 일반 회원으로 가입한 계정
                    - `type=OWNER`: 사장으로 가입한 계정(사업자 정보 보유). 승인 여부와 무관하게 가입 시점에 셉니다
                    - 회원 기준은 요약의 전체 회원과 같습니다(관리자·탈퇴·가입 미완료 제외)
                    - `days`는 1~90, 생략 시 7입니다
                    """
    )
    @GetMapping("/signups")
    public ResponseEntity<ApiResponse<AdminSignupTrendResponseDto>> getSignupTrend(
            @Parameter(description = "가입자 구분 (GENERAL·OWNER). 생략 시 GENERAL")
            @RequestParam(required = false) SignupMemberType type,
            @Parameter(description = "조회 일수 (1~90). 생략 시 7", example = "7")
            @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSignupTrend(type, days)));
    }

    @Operation(
            summary = "대시보드 실시간 활동",
            description = """
                    회원 가입·가게 등록·결제 완료·신고 접수를 최신순으로 합쳐 반환합니다.

                    - `MEMBER_SIGNUP`: description은 대표 동네(인증 완료)의 구 이름. 없으면 null
                    - `STORE_REGISTERED`: description은 상점명. 공개 노출되는 상점(사장 승인 완료·계정 활성·상점 미정지)만. 시각은 승인 시각이 아니라 상점 생성 시각(사장 가입 시점)
                    - `PAYMENT_COMPLETED`: description은 상점명, `amount`는 결제 금액. 결제 완료 시각(paidAt) 기준. 전액 취소·환불된 결제는 제외, 부분 환불은 포함(amount는 원 결제 금액)
                    - `REPORT_RECEIVED`: description은 "신고 사유 · 신고 대상"
                    - `limit`은 1~50, 생략 시 10입니다
                    """
    )
    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<List<AdminActivityResponseDto>>> getActivities(
            @Parameter(description = "조회 건수 (1~50). 생략 시 10", example = "10")
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardActivityService.getRecentActivities(limit)));
    }

    @Operation(
            summary = "대시보드 지역별 활동 사용자",
            description = """
                    대표 동네 기준 구·군별 회원 수를 많은 순으로 반환합니다.

                    - 대표 동네의 동네 인증을 마친 회원만 셉니다
                    - 회원 기준은 요약의 전체 회원과 같습니다(관리자·탈퇴·가입 미완료 제외)
                    - 같은 이름의 구(예: 서울 중구·부산 중구)가 섞이지 않도록 시·도와 함께 묶습니다
                    - `limit`은 1~50, 생략 시 6입니다
                    """
    )
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<AdminRegionMemberResponseDto>>> getRegionMembers(
            @Parameter(description = "조회할 구·군 수 (1~50). 생략 시 6", example = "6")
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getRegionMembers(limit)));
    }
}
