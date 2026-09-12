package com.eeum.eeum.api.settlement;
import com.eeum.eeum.application.settlement.dto.request.ManualPayoutCompleteRequestDto;
import com.eeum.eeum.application.settlement.dto.response.WeeklySettlementResponseDto;
import com.eeum.eeum.application.settlement.service.ManualSettlementPayoutService;
import com.eeum.eeum.application.settlement.service.SettlementQueryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
@Tag(name = "21. Admin Settlement", description = "관리자 정산 지급 API")
@SecurityRequirement(name = "bearerAuth")
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/admin/settlements")
public class AdminSettlementController {
 private final SettlementQueryService queryService; private final ManualSettlementPayoutService payoutService;
 @Operation(summary = "주간 정산 목록 조회")
 @GetMapping public ResponseEntity<ApiResponse<Page<WeeklySettlementResponseDto>>> list(@PageableDefault(size=20) Pageable p){return ResponseEntity.ok(ApiResponse.success(queryService.getAdminWeeklySettlements(p)));}
 @Operation(summary = "정산 지급 작업 claim")
 @PostMapping("/{id}/claim") public ResponseEntity<ApiResponse<String>> claim(@PathVariable @Positive Long id){String token=UUID.randomUUID().toString(); payoutService.claim(SecurityUtil.getCurrentAccountId(),id,token,LocalDateTime.now().plusMinutes(10));return ResponseEntity.ok(ApiResponse.success(token));}
 @Operation(summary = "수동 지급 완료 처리")
 @PostMapping("/{id}/complete") public ResponseEntity<ApiResponse<Void>> complete(@PathVariable @Positive Long id,@Valid @RequestBody ManualPayoutCompleteRequestDto r){payoutService.complete(SecurityUtil.getCurrentAccountId(),id,r.claimToken(),r.payoutReference());return ResponseEntity.ok(ApiResponse.success());}
}
