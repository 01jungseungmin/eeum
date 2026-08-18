package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.application.used.service.UsedProductImageService;
import com.eeum.eeum.application.used.service.UsedProductService;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/used-products")
@RequiredArgsConstructor
@Tag(name = "31. Used Product", description = "중고거래 게시글 API")
public class UsedProductController {

    private final UsedProductService usedProductService;
    private final UsedProductImageService usedProductImageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "내 동네 중고 게시글 목록",
            description = "regionId를 지정하면 해당 동네만, 생략하면 내 인증 활동 지역 전체를 조회합니다. " +
                    "삭제되거나 숨김 처리된 게시글은 제외됩니다. 무한 스크롤용 Slice로 반환합니다. " +
                    "정렬은 createdAt·price·favoriteCount·viewCount만 지원하며, 그 외 값은 무시하고 최신순으로 조회합니다."
    )
    public ResponseEntity<ApiResponse<Slice<UsedProductSummaryResponseDto>>> getRegionProducts(
            @Parameter(description = "거래 지역 ID. 생략 시 내 인증 지역 전체")
            @RequestParam(required = false) Long regionId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long viewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.getRegionProducts(viewerId, regionId, pageable)));
    }

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
            description = "삭제된 게시글은 조회되지 않습니다. 관리자가 숨긴 게시글은 작성자 본인에게만 보입니다. " +
                    "본인 글이 아닌 경우 조회수가 1 증가합니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> getUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId
    ) {
        Long viewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.getDetailAndCountView(viewerId, usedProductId)));
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

    // ===================== 사진 =====================

    @PostMapping("/{usedProductId}/images")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 사진 등록",
            description = "게시글당 최대 10장입니다. 사진이 없던 게시글의 첫 장은 자동으로 대표 사진이 됩니다."
    )
    public ResponseEntity<ApiResponse<List<UsedProductImageResponseDto>>> addImages(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId,
            @Valid @RequestBody ImageUploadListRequestDto request
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        usedProductImageService.addImages(sellerId, usedProductId, request)));
    }

    @DeleteMapping("/{usedProductId}/images/{imageId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고 게시글 사진 삭제",
            description = "삭제 후 노출 순서가 1부터 다시 매겨집니다. 대표 사진을 삭제하면 남은 첫 사진이 대표가 됩니다."
    )
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId,
            @Parameter(description = "이미지 ID") @PathVariable Long imageId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        usedProductImageService.deleteImage(sellerId, usedProductId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{usedProductId}/images/{imageId}/thumbnail")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "대표 사진 변경", description = "지정한 사진을 대표로 바꿉니다. 대표는 항상 한 장입니다.")
    public ResponseEntity<ApiResponse<Void>> changeThumbnail(
            @Parameter(description = "게시글 ID") @PathVariable Long usedProductId,
            @Parameter(description = "이미지 ID") @PathVariable Long imageId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        usedProductImageService.changeThumbnail(sellerId, usedProductId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
