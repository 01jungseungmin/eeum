package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiSavingPlanSaveRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.service.AiOperationRiskService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/operation-risks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiOperationRiskController {

    private final AiOperationRiskService aiOperationRiskService;

    @Operation(summary = "운영 위험 조기정보 조회 (요약)",
            description = "종합 위험 신호와 AI 판단을 조회합니다. (Basic 이상)")
    @GetMapping
    public ResponseEntity<ApiResponse<AiOperationRiskResponseDto>> getRisks() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiOperationRiskService.getRisks(ownerId)));
    }

    @Operation(summary = "운영 위험 조기정보 상세 조회",
            description = "대응 체크리스트를 포함한 상세 정보를 조회합니다. (Pro 전용)")
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<AiOperationRiskResponseDto>> getRiskDetail() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiOperationRiskService.getRiskDetail(ownerId)));
    }

    @Operation(summary = "사장님 실측값 입력",
            description = "월 전력 사용량 등 실측값을 입력합니다. 입력 후 데이터 출처가 OWNER_INPUT으로 반영됩니다. 동일 연월 입력은 갱신됩니다.")
    @PostMapping("/owner-input")
    public ResponseEntity<ApiResponse<Void>> saveOwnerInput(
            @Valid @RequestBody AiOwnerMetricInputRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        aiOperationRiskService.saveOwnerInput(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    @Operation(summary = "절감 계획 생성",
            description = "절감 항목/난이도/예상 절감액이 포함된 계획을 생성합니다. 실측값이 없으면 절감액은 null입니다. (Pro 전용)")
    @PostMapping("/saving-plan")
    public ResponseEntity<ApiResponse<AiSavingPlanResponseDto>> createSavingPlan() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiOperationRiskService.createSavingPlan(ownerId)));
    }

    @Operation(summary = "절감 계획 저장",
            description = "생성된 절감 계획을 저장합니다. 저장 후 운영 위험 조회의 hasSavedPlan이 true가 됩니다. (Pro 전용)")
    @PostMapping("/saving-plan/save")
    public ResponseEntity<ApiResponse<AiSavingPlanResponseDto>> saveSavingPlan(
            @RequestBody(required = false) AiSavingPlanSaveRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiOperationRiskService.saveSavingPlan(ownerId, request)));
    }

    @Operation(summary = "전력 사용 리포트 조회",
            description = "최근 6개월 월별 사용량과 핵심 진단을 조회합니다. 실측값 입력분을 우선 사용합니다. (Pro 전용)")
    @GetMapping("/electricity-report")
    public ResponseEntity<ApiResponse<AiElectricityReportResponseDto>> getElectricityReport() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiOperationRiskService.getElectricityReport(ownerId)));
    }
}
