package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.account.service.OwnerApprovalService;
import com.eeum.eeum.application.product.dto.request.RepresentativeMenuCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.SettlementAccountRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessHourUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreBusinessInfoRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerChecklistResponseDto;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

    @Operation(
            summary = "입점 심사용 상점 기본 정보 입력/수정",
            description = "입점 심사를 위해 업종과 상점 설명을 입력하거나 수정합니다. 상점명, 주소, 전화번호는 사장 회원가입 시 입력된 값을 사용합니다."
    )
    @PatchMapping("/business-info")
    public ResponseEntity<ApiResponse<Void>> updateStoreBusinessInfo(
            @Valid @RequestBody StoreBusinessInfoRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ownerApprovalService.updateStoreBusinessInfo(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "입점 심사용 상점 영업시간 입력/수정",
            description = "입점 심사를 위해 요일별 영업시간을 입력하거나 수정합니다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "입점 심사용 상점 영업시간 입력/수정",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "요일별 영업시간 예시",
                            value = """
                                {
                                  "businessHours": [
                                    {
                                      "dayOfWeek": "MONDAY",
                                      "closed": false,
                                      "openTime": "09:00",
                                      "closeTime": "19:00"
                                    },
                                    {
                                      "dayOfWeek": "TUESDAY",
                                      "closed": false,
                                      "openTime": "09:00",
                                      "closeTime": "19:00"
                                    },
                                    {
                                      "dayOfWeek": "WEDNESDAY",
                                      "closed": false,
                                      "openTime": "09:00",
                                      "closeTime": "19:00"
                                    },
                                    {
                                      "dayOfWeek": "THURSDAY",
                                      "closed": false,
                                      "openTime": "09:00",
                                      "closeTime": "19:00"
                                    },
                                    {
                                      "dayOfWeek": "FRIDAY",
                                      "closed": false,
                                      "openTime": "09:00",
                                      "closeTime": "19:00"
                                    },
                                    {
                                      "dayOfWeek": "SATURDAY",
                                      "closed": false,
                                      "openTime": "10:00",
                                      "closeTime": "18:00"
                                    },
                                    {
                                      "dayOfWeek": "SUNDAY",
                                      "closed": true,
                                      "openTime": null,
                                      "closeTime": null
                                    }
                                  ]
                                }
                                """
                    )
            )
    )
    @PutMapping("/approval/business-hours")
    public ResponseEntity<ApiResponse<Void>> updateBusinessHours(
            @Valid @RequestBody StoreBusinessHourUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ownerApprovalService.updateBusinessHours(accountId, request);
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

    @Operation(
            summary = "대표 메뉴 등록/수정",
            description = "사장 입점 심사를 위해 대표 메뉴를 등록하거나 수정합니다. 기존 대표 메뉴가 없으면 새로 생성하고, 이미 있으면 해당 대표 메뉴 정보를 수정합니다."
    )
    @PutMapping("/representative-menu")
    public ResponseEntity<ApiResponse<Void>> saveRepresentativeMenu(
            @Valid @RequestBody RepresentativeMenuCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        ownerApprovalService.saveRepresentativeMenu(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
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