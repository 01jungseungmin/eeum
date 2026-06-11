package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.product.dto.request.ProductCategoryRequestDto;
import com.eeum.eeum.application.product.dto.response.ProductCategoryResponseDto;
import com.eeum.eeum.application.product.service.ProductCategoryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "07. Owner - Product Category", description = "사장 상품 카테고리 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/product-categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @Operation(summary = "[사장] 상품 카테고리 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductCategoryResponseDto>>> getCategories() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.getCategories(accountId)));
    }

    @Operation(summary = "[사장] 상품 카테고리 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductCategoryResponseDto>> createCategory(
            @Valid @RequestBody ProductCategoryRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.createCategory(accountId, request)));
    }

    @Operation(summary = "[사장] 상품 카테고리 수정")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<ProductCategoryResponseDto>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody ProductCategoryRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.updateCategory(accountId, categoryId, request)));
    }

    @Operation(summary = "[사장] 상품 카테고리 활성화")
    @PatchMapping("/{categoryId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateCategory(
            @PathVariable Long categoryId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productCategoryService.activateCategory(accountId, categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[사장] 상품 카테고리 비활성화")
    @PatchMapping("/{categoryId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(
            @PathVariable Long categoryId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productCategoryService.deactivateCategory(accountId, categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[사장] 상품 카테고리 삭제")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable Long categoryId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productCategoryService.deleteCategory(accountId, categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
