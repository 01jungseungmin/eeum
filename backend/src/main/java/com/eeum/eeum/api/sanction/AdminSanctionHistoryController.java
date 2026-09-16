package com.eeum.eeum.api.sanction;

import com.eeum.eeum.application.sanction.dto.response.SanctionHistoryResponseDto;
import com.eeum.eeum.application.sanction.service.SanctionHistoryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "34. Admin - Sanction History", description = "관리자 회원·상점 제재 이력 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/sanction-histories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminSanctionHistoryController {

    private final SanctionHistoryService sanctionHistoryService;

    @Operation(summary = "[관리자] 회원 제재 이력 조회")
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<ApiResponse<Page<SanctionHistoryResponseDto>>> getAccountHistories(
            @PathVariable @Positive Long accountId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                sanctionHistoryService.getAccountHistories(accountId, pageable)
        ));
    }

    @Operation(summary = "[관리자] 상점 제재 이력 조회")
    @GetMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<Page<SanctionHistoryResponseDto>>> getStoreHistories(
            @PathVariable @Positive Long storeId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                sanctionHistoryService.getStoreHistories(storeId, pageable)
        ));
    }
}
