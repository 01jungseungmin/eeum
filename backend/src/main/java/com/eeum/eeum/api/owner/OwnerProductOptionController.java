package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.product.dto.request.ProductOptionCreateRequestDto;
import com.eeum.eeum.application.product.dto.request.ProductOptionUpdateRequestDto;
import com.eeum.eeum.application.product.dto.response.ProductOptionDto;
import com.eeum.eeum.application.product.service.ProductOptionService;
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

@Tag(name = "09. Owner - ProductOption", description = "상품 옵션 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/products/{productId}/options")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerProductOptionController {

    private final ProductOptionService productOptionService;

    @Operation(summary = "상품 옵션 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductOptionDto>>> getOptions(
            @PathVariable Long productId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productOptionService.getOptions(accountId, productId)));
    }

    @Operation(summary = "옵션 그룹 생성 (선택지 포함)")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductOptionDto>> createOption(
            @PathVariable Long productId,
            @Valid @RequestBody ProductOptionCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productOptionService.createOption(accountId, productId, request)));
    }

    @Operation(summary = "옵션 그룹 전체 교체")
    @PutMapping("/{optionId}")
    public ResponseEntity<ApiResponse<ProductOptionDto>> replaceOption(
            @PathVariable Long productId,
            @PathVariable Long optionId,
            @Valid @RequestBody ProductOptionUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productOptionService.replaceOption(accountId, productId, optionId, request)));
    }

    @Operation(summary = "선택지 품절 토글")
    @PatchMapping("/items/{itemId}/availability")
    public ResponseEntity<ApiResponse<Void>> toggleItemAvailability(
            @PathVariable Long productId,
            @PathVariable Long itemId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productOptionService.toggleItemAvailability(accountId, productId, itemId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "옵션 그룹 삭제")
    @DeleteMapping("/{optionId}")
    public ResponseEntity<ApiResponse<Void>> deleteOption(
            @PathVariable Long productId,
            @PathVariable Long optionId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productOptionService.deleteOption(accountId, productId, optionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}