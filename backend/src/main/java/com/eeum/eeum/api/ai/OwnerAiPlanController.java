package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiPlanPaymentCompleteRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.application.ai.service.AiPlanSubscriptionService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiPlanController {

    private final AiPlanSubscriptionService aiPlanSubscriptionService;

    @Operation(summary = "AI 플랜 구독 결제 요청",
            description = "BASIC/PRO 플랜 구독 결제를 생성합니다. 반환된 paymentId로 프론트에서 PortOne 결제를 진행합니다. "
                    + "결제 성공 전에는 플랜이 변경되지 않습니다.")
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<AiPlanSubscribeResponseDto>> subscribe(
            @Valid @RequestBody AiPlanSubscribeRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiPlanSubscriptionService.requestSubscription(ownerId, request)));
    }

    @Operation(summary = "AI 플랜 결제 완료 검증",
            description = "PortOne 결제 완료 후 검증하고 구독을 반영합니다. Webhook과 중복 호출돼도 1회만 반영됩니다.")
    @PostMapping("/payments/complete")
    public ResponseEntity<ApiResponse<AiPlanSubscribeResponseDto>> completePayment(
            @Valid @RequestBody AiPlanPaymentCompleteRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                aiPlanSubscriptionService.completePayment(ownerId, request.getPaymentId())));
    }

    @Operation(summary = "AI 플랜 구독 취소",
            description = "기본 정책은 결제 기간 종료일까지 유지 후 FREE 전환입니다 (ai.plan.cancel-immediately=true면 즉시 전환).")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        aiPlanSubscriptionService.cancelSubscription(ownerId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
