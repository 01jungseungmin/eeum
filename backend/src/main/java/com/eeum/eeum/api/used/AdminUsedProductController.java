package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.service.AdminUsedProductService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;

@Tag(name = "32. Admin - Used Product", description = "관리자 중고거래 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/admin/used")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsedProductController {

    private final AdminUsedProductService adminUsedProductService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UsedProductSummaryResponseDto>>> getUsedProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUsedProductService.getProducts(pageable)));
    }

    @GetMapping("/{usedProductId}")
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> getUsedProduct(
            @PathVariable @Positive Long usedProductId) {
        return ResponseEntity.ok(ApiResponse.success(adminUsedProductService.getDetail(usedProductId)));
    }

    @PatchMapping("/{usedProductId}/hide")
    public ResponseEntity<ApiResponse<Void>> hideUsedProduct(
            @PathVariable @Positive Long usedProductId) {
        adminUsedProductService.hide(usedProductId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "중고 게시글 숨김 해제",
            description = "신고 조치로 숨겨진 게시글의 노출을 되돌립니다. 거래 상태는 바뀌지 않습니다. " +
                    "숨김은 신고 처리(HIDE_POST)로 적용됩니다."
    )
    @PatchMapping("/{usedProductId}/show")
    public ResponseEntity<ApiResponse<Void>> showUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId
    ) {
        adminUsedProductService.show(usedProductId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
