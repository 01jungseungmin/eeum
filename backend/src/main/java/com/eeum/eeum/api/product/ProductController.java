package com.eeum.eeum.api.product;

import com.eeum.eeum.application.product.dto.request.ProductCreateRequestDto;
import com.eeum.eeum.application.product.service.ProductService;
import com.eeum.eeum.common.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "사장 상점에 상품을 등록합니다. 대표 메뉴는 productType=MENU로 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createProduct(
            @Valid @RequestBody ProductCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        productService.createProduct(accountId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}