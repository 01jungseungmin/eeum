package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.response.AiActivitySummaryResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiManagerDashboardResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanResponseDto;
import com.eeum.eeum.application.ai.service.AiActivityService;
import com.eeum.eeum.application.ai.service.AiDashboardService;
import com.eeum.eeum.application.ai.service.AiPlanService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiDashboardController {

    private final AiDashboardService aiDashboardService;
    private final AiActivityService aiActivityService;
    private final AiPlanService aiPlanService;

    @Operation(summary = "AI 매니저 메인 대시보드 조회",
            description = "고객 케어 카드, 리뷰/문의, 이벤트 성과, 생활권 매칭, 운영 위험, AI 활동 요약을 한 번에 조회합니다. 플랜 미달 섹션은 null로 내려갑니다.")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AiManagerDashboardResponseDto>> getDashboard() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiDashboardService.getDashboard(ownerId)));
    }

    @Operation(summary = "AI 활동 요약 조회",
            description = "최근 30일간 AI가 생성한 초안/발송 활동을 집계합니다. (Basic 이상)")
    @GetMapping("/activities/summary")
    public ResponseEntity<ApiResponse<AiActivitySummaryResponseDto>> getActivitySummary() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiActivityService.getActivitySummary(ownerId)));
    }

    @Operation(summary = "플랜 관리 조회",
            description = "현재 플랜, 플랜별 가격/기능, 이번 달 사용량과 월 제한을 조회합니다.")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<AiPlanResponseDto>> getPlans() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiPlanService.getPlans(ownerId)));
    }
}
