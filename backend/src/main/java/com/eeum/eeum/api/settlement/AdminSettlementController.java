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
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/admin/settlements")
public class AdminSettlementController {
 private final SettlementQueryService queryService; private final ManualSettlementPayoutService payoutService;
 @GetMapping public ResponseEntity<ApiResponse<Page<WeeklySettlementResponseDto>>> list(@PageableDefault(size=20) Pageable p){return ResponseEntity.ok(ApiResponse.success(queryService.getAdminWeeklySettlements(p)));}
 @PostMapping("/{id}/claim") public ResponseEntity<ApiResponse<String>> claim(@PathVariable @Positive Long id){String token=UUID.randomUUID().toString(); payoutService.claim(SecurityUtil.getCurrentAccountId(),id,token,LocalDateTime.now().plusMinutes(10));return ResponseEntity.ok(ApiResponse.success(token));}
 @PostMapping("/{id}/complete") public ResponseEntity<ApiResponse<Void>> complete(@PathVariable @Positive Long id,@Valid @RequestBody ManualPayoutCompleteRequestDto r){payoutService.complete(SecurityUtil.getCurrentAccountId(),id,r.claimToken(),r.payoutReference());return ResponseEntity.ok(ApiResponse.success());}
}
