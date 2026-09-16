package com.eeum.eeum.api.account;

import com.eeum.eeum.application.account.dto.request.LocationDto;
import com.eeum.eeum.application.account.dto.request.RegionRequestDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.application.account.service.AccountRegionService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. AccountRegion", description = "활동 지역 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/me/regions")
@RequiredArgsConstructor
public class AccountRegionController {

    private final AccountRegionService accountRegionService;

    @Operation(summary = "활동 지역 목록 조회", description = "등록된 활동 지역 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountRegionResponseDto>>> getRegions() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        List<AccountRegionResponseDto> response = accountRegionService.getRegions(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "특정 활동 지역 조회", description = "등록된 특정 활동 지역을 조회합니다.")
    @GetMapping("/{accountRegionId}")
    public ResponseEntity<ApiResponse<AccountRegionResponseDto>> getRegion(
            @PathVariable Long accountRegionId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        AccountRegionResponseDto response = accountRegionService.getRegion(accountId, accountRegionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "활동 지역 등록", description = "활동 지역을 등록합니다. 최대 2개까지 등록 가능합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AccountRegionResponseDto>> addRegion(
            @Valid @RequestBody RegionRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        AccountRegionResponseDto response = accountRegionService.addRegion(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "활동 지역 GPS 인증", description = "현재 GPS 위치로 등록된 지역을 인증합니다.")
    @PatchMapping("/{accountRegionId}/verify")
    public ResponseEntity<ApiResponse<AccountRegionResponseDto>> verifyRegion(
            @PathVariable Long accountRegionId,
            @Valid @RequestBody LocationDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        AccountRegionResponseDto response = accountRegionService.verifyRegion(accountId, accountRegionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "대표 지역 설정", description = "등록된 활동 지역 중 대표 지역을 설정합니다.")
    @PatchMapping("/{accountRegionId}/primary")
    public ResponseEntity<ApiResponse<Void>> setPrimaryRegion(
            @PathVariable Long accountRegionId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountRegionService.setPrimaryRegion(accountId, accountRegionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "활동 지역 삭제", description = "등록된 활동 지역을 삭제합니다.")
    @DeleteMapping("/{accountRegionId}")
    public ResponseEntity<ApiResponse<Void>> deleteRegion(
            @PathVariable Long accountRegionId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        accountRegionService.deleteRegion(accountId, accountRegionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}