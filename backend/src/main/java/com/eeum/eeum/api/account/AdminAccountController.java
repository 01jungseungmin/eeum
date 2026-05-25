package com.eeum.eeum.api.account;

import com.eeum.eeum.application.account.dto.request.RejectRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountDetailResponseDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerResponseDto;
import com.eeum.eeum.application.account.service.AdminAccountService;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Account", description = "[관리자] 회원 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(summary = "[관리자] 회원 목록 조회", description = "전체 회원 목록을 페이징으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AccountResponseDto>>> getAccounts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "회원 상태 필터 (ACTIVE / SUSPENDED / WITHDRAWN)")
            @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "권한 필터 (ROLE_USER / ROLE_OWNER / ROLE_ADMIN)")
            @RequestParam(required = false) AccountRole role,
            @Parameter(description = "닉네임 또는 이메일 검색 키워드")
            @RequestParam(required = false) String keyword
    ) {
        Page<AccountResponseDto> response = adminAccountService.getAccounts(pageable, status, role, keyword);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "[관리자] 탈퇴 예정 회원 목록 조회", description = "탈퇴 처리된 회원 목록을 페이징으로 조회합니다.")
    @GetMapping("/withdrawn")
    public ResponseEntity<ApiResponse<Page<AccountResponseDto>>> getWithdrawnAccounts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AccountResponseDto> response = adminAccountService.getWithdrawnAccounts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "[관리자] 회원 상세 조회", description = "특정 회원의 상세 정보를 조회합니다.")
    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountDetailResponseDto>> getAccountDetail(
            @Parameter(description = "조회할 회원 ID", required = true, example = "1")
            @PathVariable @Positive Long accountId
    ) {
        AccountDetailResponseDto response = adminAccountService.getAccountDetail(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "[관리자] 회원 정지", description = "특정 회원을 정지 처리합니다.")
    @PatchMapping("/{accountId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendAccount(
            @Parameter(description = "정지할 회원 ID", required = true, example = "1")
            @PathVariable @Positive Long accountId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.suspendAccount(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 회원 정지 해제", description = "정지된 회원을 활성화합니다.")
    @PatchMapping("/{accountId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateAccount(
            @Parameter(description = "활성화할 회원 ID", required = true, example = "1")
            @PathVariable @Positive Long accountId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.activateAccount(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 탈퇴 취소", description = "탈퇴 신청한 회원의 탈퇴를 취소합니다.")
    @PatchMapping("/{accountId}/withdrawal/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelWithdrawal(
            @Parameter(description = "탈퇴를 취소할 회원 ID", required = true, example = "1")
            @PathVariable @Positive Long accountId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.cancelWithdrawal(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 회원 강제 탈퇴", description = "특정 회원을 강제 탈퇴 처리합니다.")
    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<Void>> forceDeleteAccount(
            @Parameter(description = "강제 탈퇴할 회원 ID", required = true, example = "1")
            @PathVariable @Positive Long accountId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.forceDeleteAccount(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 사장 신청 목록 조회", description = "사장 회원 승인 대기 목록을 조회합니다.")
    @GetMapping("/owners/applications")
    public ResponseEntity<ApiResponse<Page<AccountDetailResponseDto>>> getOwnerRequests(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "승인 상태 필터 (PENDING / APPROVED / REJECTED)")
            @RequestParam(required = false) ApprovalStatus approvalStatus
    ) {
        Page<AccountDetailResponseDto> response = adminAccountService.getOwnerRequests(pageable, approvalStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "[관리자] 사장 신청 상세 조회", description = "사장 회원 신청 상세 정보를 조회합니다.")
    @GetMapping("/owners/{ownerInfoId}")
    public ResponseEntity<ApiResponse<OwnerResponseDto>> getOwnerApplicationDetail(
            @Parameter(description = "조회할 사장 신청 ID", required = true, example = "1")
            @PathVariable @Positive Long ownerInfoId
    ) {
        OwnerResponseDto response = adminAccountService.getOwnerApplicationDetail(ownerInfoId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "[관리자] 사장 승인", description = "사장 회원 신청을 승인합니다.")
    @PatchMapping("/owners/{ownerInfoId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveOwner(
            @Parameter(description = "승인할 사장 신청 ID", required = true, example = "1")
            @PathVariable @Positive Long ownerInfoId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.approveOwner(adminId, ownerInfoId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 사장 거절", description = "사장 회원 신청을 거절합니다.")
    @PatchMapping("/owners/{ownerInfoId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectOwner(
            @Parameter(description = "거절할 사장 신청 ID", required = true, example = "1")
            @PathVariable @Positive Long ownerInfoId,
            @Valid @RequestBody RejectRequestDto request
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminAccountService.rejectOwner(adminId, ownerInfoId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}