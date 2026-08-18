package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.service.UsedProductService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/used-products")
@RequiredArgsConstructor
@Tag(name = "31. Used Product", description = "중고거래 게시글 API")
public class UsedProductController {

    private final UsedProductService usedProductService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 등록",
            description = "거래 희망 지역은 본인의 GPS 인증이 완료된 활동 지역이어야 합니다. " +
                    "가격은 거래 유형에 따라 강제됩니다 — FIXED는 0보다 큰 값, FREE는 0, NEGOTIABLE은 보내지 않습니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> createUsedProduct(
            @Valid @RequestBody UsedProductCreateRequestDto request
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(usedProductService.create(sellerId, request)));
    }

    @GetMapping("/{usedProductId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 상세 조회",
            description = "삭제된 게시글은 조회되지 않습니다. 관리자가 숨긴 게시글은 작성자 본인에게만 보입니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> getUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId
    ) {
        Long viewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.getDetail(viewerId, usedProductId)));
    }

    @PatchMapping("/{usedProductId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 수정",
            description = "작성자 본인만 수정할 수 있습니다. 거래 희망 지역은 변경할 수 없습니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> updateUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId,
            @Valid @RequestBody UsedProductUpdateRequestDto request
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.update(sellerId, usedProductId, request)));
    }

    @DeleteMapping("/{usedProductId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 삭제",
            description = "작성자 본인만 삭제할 수 있습니다. 예약 중인 게시글은 예약을 먼저 취소해야 합니다."
    )
    public ResponseEntity<ApiResponse<Void>> deleteUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        usedProductService.delete(sellerId, usedProductId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
