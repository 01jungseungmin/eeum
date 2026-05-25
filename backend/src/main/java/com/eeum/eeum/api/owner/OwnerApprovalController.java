package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.account.service.OwnerApprovalService;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBasicInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.common.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner - Approval", description = "사장 입점 심사 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'USER')")
public class OwnerApprovalController {

    private final OwnerApprovalService ownerApprovalService;

    @Operation(summary = "입점 심사 체크리스트 조회",
            description = "사장 입점 심사 체크리스트 완료 상태를 조회합니다.")
    @GetMapping("/checklist")
    public ResponseEntity<ApiResponse<OwnerChecklistResponseDto>> getChecklist() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerApprovalService.getChecklist(accountId)));
    }

    @Operation(summary = "영업시간 및 상점 설명 수정",
            description = "영업시간과 상점 설명을 입력합니다.")
    @PatchMapping("/business-info")
    public ResponseEntity<ApiResponse<Void>> updateStoreBasicInfo(
            @Valid @RequestBody StoreBasicInfoRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ownerApprovalService.updateStoreBasicInfo(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "정산 계좌 등록/수정",
            description = "정산 계좌를 등록하거나 수정합니다.")
    @PutMapping("/settlement-account")
    public ResponseEntity<ApiResponse<SettlementAccountResponseDto>> saveSettlementAccount(
            @Valid @RequestBody SettlementAccountRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerApprovalService.saveSettlementAccount(accountId, request)));
    }

    @Operation(summary = "입점 심사 요청",
            description = "모든 필수 항목 완료 후 관리자 심사를 요청합니다.")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Void>> requestReview() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ownerApprovalService.requestReview(accountId);
        return ResponseEntity.ok(ApiResponse.success(null, "심사 요청이 완료되었습니다"));
    }
}