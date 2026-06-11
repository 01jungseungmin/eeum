package com.eeum.eeum.api.account;

import com.eeum.eeum.application.account.dto.request.ChangePasswordRequestDto;
import com.eeum.eeum.application.account.dto.request.FcmTokenRequestDto;
import com.eeum.eeum.application.account.dto.request.OwnerInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.account.dto.request.WithdrawRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountResponseDto;
import com.eeum.eeum.application.account.dto.response.MyPageResponseDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationDetailResponseDto;
import com.eeum.eeum.application.account.service.AccountService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. Account", description = "회원 정보 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/me")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    // ===================== 내 정보 (USER) =====================

    @Operation(summary = "내 정보 조회", description = "마이페이지 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<MyPageResponseDto>> getMyPage() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        MyPageResponseDto response = accountService.getMyPage(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 정보 수정", description = "닉네임, 프로필 이미지를 수정합니다.")
    @PatchMapping
    public ResponseEntity<ApiResponse<AccountResponseDto>> updateMyInfo(
            @Valid @RequestBody UpdateInfoRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        AccountResponseDto response = accountService.updateMyInfo(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "비밀번호 변경", description = "재인증 토큰 검증 후 비밀번호를 변경합니다. (LOCAL 계정만)")
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountService.changePassword(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "회원 탈퇴", description = "재인증 토큰 검증 후 회원을 탈퇴 처리합니다. (30일 후 물리 삭제)")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Valid @RequestBody WithdrawRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountService.withdraw(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "FCM 토큰 등록/갱신", description = "푸시 알림을 위한 FCM 토큰을 등록합니다.")
    @PutMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @Valid @RequestBody FcmTokenRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountService.updateFcmToken(accountId, request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 사장 회원 정보 =====================

    @Operation(summary = "내 사업자 정보 조회", description = "내 사업자 정보와 승인 상태를 조회합니다.")
    @GetMapping("/owner")
    public ResponseEntity<ApiResponse<OwnerApplicationDetailResponseDto>> getMyOwnerInfo() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        OwnerApplicationDetailResponseDto response = accountService.getMyOwnerInfo(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "사장 정보 수정", description = "전화번호 및 사업자번호를 수정합니다. 사업자번호 변경 시 재심사됩니다.")
    @PutMapping("/owner")
    public ResponseEntity<ApiResponse<Void>> updateOwnerInfo(
            @Valid @RequestBody OwnerInfoRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountService.updateOwnerInfo(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}