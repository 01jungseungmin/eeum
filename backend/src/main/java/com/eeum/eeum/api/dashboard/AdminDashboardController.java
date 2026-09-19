package com.eeum.eeum.api.dashboard;

import com.eeum.eeum.application.dashboard.dto.response.AdminDashboardSummaryResponseDto;
import com.eeum.eeum.application.dashboard.service.AdminDashboardService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "35. Admin - Dashboard", description = "관리자 대시보드 지표")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "대시보드 KPI 요약",
            description = """
                    관리자 대시보드 상단 카드(전체 회원·활성 사업장·오늘 거래·처리 대기) 지표를 한 번에 반환합니다.

                    **집계 기준**
                    - 회원: 일반·사장 계정 중 활성·정지 상태. 관리자·탈퇴·가입 미완료 제외
                    - 활성 사업장: 사용자에게 공개 노출되는 상점(사장 승인 완료·계정 활성·상점 미정지)
                    - "이번 주"는 월요일 00:00부터 현재까지입니다
                    - 거래: 결제 완료 이후 상태(PAID·CONFIRMED·READY·COMPLETED) 주문
                    - 전일 대비는 어제 같은 시각까지의 거래와 비교합니다. 어제 거래가 0건이면 `orderChangeRate`는 null입니다
                    - 처리 대기: 심사 요청된 사장 승인 대기 + 미처리 신고 + 미답변 관리자 문의
                    """
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryResponseDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary()));
    }
}
