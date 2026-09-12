package com.eeum.eeum.api.settlement;

import com.eeum.eeum.application.settlement.dto.response.OwnerRevenueResponseDto;
import com.eeum.eeum.application.settlement.dto.response.WeeklySettlementResponseDto;
import com.eeum.eeum.application.settlement.service.SettlementQueryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "21. Owner Settlement", description = "사장 정산 조회 API")
@SecurityRequirement(name = "bearerAuth")
@RestController @RequiredArgsConstructor @RequestMapping("/owner/settlements")
public class OwnerSettlementController {
    private final SettlementQueryService settlementQueryService;
    @Operation(summary = "사장 수익 원장 조회")
    @GetMapping("/revenues") public ResponseEntity<ApiResponse<Page<OwnerRevenueResponseDto>>> revenues(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(ApiResponse.success(settlementQueryService.getOwnerRevenues(SecurityUtil.getCurrentAccountId(), pageable))); }
    @Operation(summary = "사장 주간 정산 조회")
    @GetMapping("/weekly") public ResponseEntity<ApiResponse<Page<WeeklySettlementResponseDto>>> weekly(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(ApiResponse.success(settlementQueryService.getOwnerWeeklySettlements(SecurityUtil.getCurrentAccountId(), pageable))); }
}
