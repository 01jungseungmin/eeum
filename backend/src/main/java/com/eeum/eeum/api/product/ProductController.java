package com.eeum.eeum.api.product;

import com.eeum.eeum.application.product.dto.request.ProductCreateRequestDto;
import com.eeum.eeum.application.product.dto.request.ProductStatusUpdateRequestDto;
import com.eeum.eeum.application.product.dto.request.ProductUpdateRequestDto;
import com.eeum.eeum.application.product.dto.request.ProductUpdateStockRequestDto;
import com.eeum.eeum.application.product.dto.response.ProductResponseDto;
import com.eeum.eeum.application.product.service.ProductImageService;
import com.eeum.eeum.application.product.service.ProductService;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
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

@Tag(name = "08. Owner - Product", description = "사장 상품 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER')")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    @Operation(
            summary = "내 상점 상품 목록 조회",
            description = "현재 로그인한 사장의 상점에 등록된 상품 목록을 조회합니다. 판매 상품, 예약 상품, 메뉴 상품을 모두 포함하며 상품 유형, 상태, 가격, 재고, 조회 수 등의 정보를 반환합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> getMyProducts() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(productService.getMyProducts(accountId)));
    }

    @Operation(
            summary = "상품 등록",
            description = "현재 로그인한 사장의 상점에 상품을 등록합니다. 상품 유형은 SALE(판매 상품), RESERVATION(예약 상품), MENU(메뉴 상품) 중 하나를 선택합니다. 사장 입점 심사 단계에서는 MENU 상품 1개 이상 등록 여부가 대표 메뉴 등록 완료 기준으로 사용됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createProduct(
            @Valid @RequestBody ProductCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productService.createProduct(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "상품 상세 조회",
            description = "현재 로그인한 사장의 상점에 등록된 특정 상품의 상세 정보를 조회합니다. 본인 상점에 속한 상품만 조회할 수 있습니다."
    )
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProduct(
            @PathVariable Long productId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(accountId, productId)));
    }

    @Operation(
            summary = "상품 수정",
            description = "현재 로그인한 사장의 상점에 등록된 상품 정보를 수정합니다. 상품명, 설명, 가격, 재고, 예약 가능 인원, 상품 유형 등을 수정할 수 있으며 본인 상점의 상품만 수정할 수 있습니다."
    )
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productService.updateProduct(accountId, productId, request)));
    }

    @Operation(
            summary = "상품 삭제",
            description = "현재 로그인한 사장의 상점에 등록된 상품을 삭제 처리합니다. 실제 데이터를 즉시 삭제하지 않고 상품 상태를 INACTIVE로 변경하여 목록과 판매 대상에서 제외합니다."
    )
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long productId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productService.deleteProduct(accountId, productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "상품 상태 변경",
            description = "현재 로그인한 사장의 상점에 등록된 상품 상태를 변경합니다. ACTIVE(판매중), SOLD_OUT(품절), INACTIVE(비공개/삭제 처리) 상태로 변경할 수 있으며 본인 상점의 상품만 변경할 수 있습니다."
    )
    @PatchMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<Void>> updateProductStatus(
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productService.updateProductStatus(accountId, productId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "재고 수정",
            description = "현재 로그인한 사장의 상점에 등록된 상품의 재고 수량을 수정합니다. 재고는 0 이상으로 설정할 수 있으며, 본인 상점의 상품만 수정할 수 있습니다."
    )
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<ApiResponse<Void>> updateStock(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateStockRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productService.updateStock(accountId, productId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ProductController에 추가
    @Operation(summary = "상품 이미지 목록 조회")
    @GetMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<List<ImageResponseDto>>> getProductImages(
            @PathVariable Long productId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getImages(accountId, productId)));
    }

    @Operation(summary = "상품 이미지 등록 (최대 20장)")
    @PostMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<List<ImageResponseDto>>> addProductImages(
            @PathVariable Long productId,
            @Valid @RequestBody ImageUploadListRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.addImages(accountId, productId, request)
        ));
    }

    @Operation(summary = "상품 대표 이미지 설정")
    @PatchMapping("/{productId}/images/{imageId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> setProductThumbnail(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productImageService.setThumbnail(accountId, productId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "상품 이미지 삭제")
    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productImageService.deleteImage(accountId, productId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}