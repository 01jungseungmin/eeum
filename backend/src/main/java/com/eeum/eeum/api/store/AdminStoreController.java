package com.eeum.eeum.api.store;

import com.eeum.eeum.application.store.dto.response.AdminStoreDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.service.AdminStoreService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "16. Admin - Store", description = "관리자 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoreController {

    private final AdminStoreService adminStoreService;

    @Operation(summary = "[관리자] 상점 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StoreListResponseDto>>> getStores(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminStoreService.getStores(keyword, status, pageable)));
    }

    @Operation(summary = "[관리자] 상점 상세 조회")
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<AdminStoreDetailResponseDto>> getStoreDetail(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminStoreService.getStoreDetail(storeId)));
    }

    @Operation(summary = "[관리자] 상점 정지")
    @PatchMapping("/{storeId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendStore(
            @PathVariable Long storeId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminStoreService.suspendStore(adminId, storeId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 상점 정지 해제")
    @PatchMapping("/{storeId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateStore(
            @PathVariable Long storeId
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        adminStoreService.activateStore(adminId, storeId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}