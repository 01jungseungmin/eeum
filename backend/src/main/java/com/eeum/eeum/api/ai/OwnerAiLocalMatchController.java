package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiExposureStatusResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiLocalMatchResponseDto;
import com.eeum.eeum.application.ai.service.AiLocalMatchService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/local-match")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiLocalMatchController {

    private final AiLocalMatchService aiLocalMatchService;

    @Operation(summary = "생활권 매칭 분석 조회",
            description = "매칭 점수, 세그먼트, 예상 노출 대상 수를 조회합니다. (Basic 이상)")
    @GetMapping
    public ResponseEntity<ApiResponse<AiLocalMatchResponseDto>> getLocalMatch() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiLocalMatchService.getLocalMatch(ownerId)));
    }

    @Operation(summary = "생활권 매칭 조건 변경",
            description = "반경/관심사/고객 유형 조건을 변경하고 변경 후 추정 노출 대상 수를 반환합니다.")
    @PatchMapping("/conditions")
    public ResponseEntity<ApiResponse<AiLocalMatchResponseDto>> updateConditions(
            @Valid @RequestBody AiLocalMatchConditionRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiLocalMatchService.updateConditions(ownerId, request)));
    }

    @Operation(summary = "생활권 매칭 노출 시작",
            description = "1차에서는 실제 광고 집행 없이 노출 상태를 기록합니다. 이미 진행 중이면 AI_INVALID_STATUS 예외. (Pro 전용)")
    @PostMapping("/exposure")
    public ResponseEntity<ApiResponse<AiExposureStatusResponseDto>> startExposure() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiLocalMatchService.startExposure(ownerId)));
    }

    @Operation(summary = "생활권 매칭 노출 상태 조회")
    @GetMapping("/exposure")
    public ResponseEntity<ApiResponse<AiExposureStatusResponseDto>> getExposureStatus() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiLocalMatchService.getExposureStatus(ownerId)));
    }

    @Operation(summary = "생활권 매칭 노출 중지",
            description = "노출을 중지합니다. 진행 중이 아니면 AI_INVALID_STATUS 예외. (Pro 전용)")
    @PostMapping("/exposure/stop")
    public ResponseEntity<ApiResponse<AiExposureStatusResponseDto>> stopExposure() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiLocalMatchService.stopExposure(ownerId)));
    }
}
