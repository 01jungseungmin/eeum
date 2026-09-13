package com.eeum.eeum.api.settlement;

import com.eeum.eeum.application.settlement.dto.request.ManualPayoutCompleteRequestDto;
import com.eeum.eeum.application.settlement.dto.response.BlockingCancellationResponseDto;
import com.eeum.eeum.application.settlement.dto.response.WeeklySettlementResponseDto;
import com.eeum.eeum.application.settlement.service.ManualSettlementPayoutService;
import com.eeum.eeum.application.settlement.service.SettlementQueryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "21. Admin Settlement", description = "관리자 정산 지급 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/settlements")
public class AdminSettlementController {

    private final SettlementQueryService queryService;
    private final ManualSettlementPayoutService payoutService;

    @Operation(summary = "주간 정산 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WeeklySettlementResponseDto>>> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getAdminWeeklySettlements(pageable)));
    }

    @Operation(summary = "정산 지급 작업 claim",
            description = "지급을 선점하고 claim 토큰을 돌려준다. 완료 처리 시 이 토큰이 필요하다.")
    @PostMapping("/{id}/claim")
    public ResponseEntity<ApiResponse<String>> claim(@PathVariable @Positive Long id) {
        String claimToken = payoutService.claim(SecurityUtil.getCurrentAccountId(), id);
        return ResponseEntity.ok(ApiResponse.success(claimToken));
    }

    @Operation(summary = "수동 지급 완료 처리")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Void>> complete(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ManualPayoutCompleteRequestDto request
    ) {
        payoutService.complete(
                SecurityUtil.getCurrentAccountId(), id, request.claimToken(), request.payoutReference());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "정산 지급을 막고 있는 취소 작업 조회",
            description = "SETTLEMENT_006으로 지급이 막혔을 때 원인 주문을 확인한다.")
    @GetMapping("/{id}/blocking-cancellations")
    public ResponseEntity<ApiResponse<List<BlockingCancellationResponseDto>>> blockingCancellations(
            @PathVariable @Positive Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.getBlockingCancellations(SecurityUtil.getCurrentAccountId(), id)));
    }

    @Operation(summary = "확정된 취소의 내부 반영 재시도",
            description = "PG가 SUCCEEDED를 반환했으나 내부 반영이 실패한 전액 취소만 재시도합니다. "
                    + "부분 취소나 PG 결과 미확정 작업은 자동 해제하지 않습니다.")
    @PostMapping("/{id}/blocking-cancellations/{orderId}/reconcile")
    public ResponseEntity<ApiResponse<Void>> reconcileConfirmedCancellation(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long orderId
    ) {
        payoutService.applyConfirmedCancellation(SecurityUtil.getCurrentAccountId(), id, orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "누락 수익 원장을 원래 주차로 재마감",
            description = "현재 주차에 섞지 않고 지급 가능 시각이 속한 원래 주차로 다시 마감합니다. "
                    + "이미 지급 완료된 주차는 변경하지 않습니다.")
    @PostMapping("/late-revenues/{ownerRevenueId}/recover")
    public ResponseEntity<ApiResponse<Void>> recoverLateRevenue(
            @PathVariable @Positive Long ownerRevenueId
    ) {
        payoutService.recoverLateRevenue(SecurityUtil.getCurrentAccountId(), ownerRevenueId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
