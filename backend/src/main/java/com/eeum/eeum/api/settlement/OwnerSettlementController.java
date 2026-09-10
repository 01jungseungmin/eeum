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

@RestController @RequiredArgsConstructor @RequestMapping("/owner/settlements")
public class OwnerSettlementController {
    private final SettlementQueryService settlementQueryService;
    @GetMapping("/revenues") public ResponseEntity<ApiResponse<Page<OwnerRevenueResponseDto>>> revenues(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(ApiResponse.success(settlementQueryService.getOwnerRevenues(SecurityUtil.getCurrentAccountId(), pageable))); }
    @GetMapping("/weekly") public ResponseEntity<ApiResponse<Page<WeeklySettlementResponseDto>>> weekly(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(ApiResponse.success(settlementQueryService.getOwnerWeeklySettlements(SecurityUtil.getCurrentAccountId(), pageable))); }
}
