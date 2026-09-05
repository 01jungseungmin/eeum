package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.dto.request.UsedProductCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductTradePartnerRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.request.UsedProductSearchRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.application.used.service.UsedProductImageService;
import com.eeum.eeum.application.used.service.UsedProductService;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadListRequestDto;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/used")
@RequiredArgsConstructor
@Tag(name = "31. Used Product", description = "중고거래 게시글 API")
public class UsedProductController {

    private final UsedProductService usedProductService;
    private final UsedProductImageService usedProductImageService;

    @GetMapping
    @SecurityRequirements   // 전역 bearer 설정 해제 — 비회원도 조회할 수 있다
    @Operation(
            summary = "동네 중고 게시글 목록",
            description = "로그인 없이 조회할 수 있습니다. regionId를 지정하면 해당 동네를, " +
                    "생략하면 내가 선택한 동네를 조회합니다(비회원이거나 선택한 동네가 없으면 regionId가 필요합니다). " +
                    "삭제되거나 숨김 처리된 게시글은 제외됩니다. 커서 무한 스크롤(Slice)입니다 — " +
                    "첫 페이지는 커서 없이 요청하고, 다음 페이지는 직전 응답의 nextCursorValue·nextCursorId를 " +
                    "cursorValue·cursorId에 그대로 담아 보냅니다(page 파라미터는 무시됩니다). " +
                    "다음 페이지가 없으면 nextCursor 값들은 null입니다. " +
                    "정렬을 바꾸면 커서도 버리고 첫 페이지부터 다시 읽어야 합니다 — 값 형식이 맞지 않으면 400입니다. " +
                    "정렬은 createdAt·price·favoriteCount·viewCount 중 하나만 지원하며(둘 이상 보내면 첫 번째만 적용), " +
                    "그 외 값은 무시하고 최신순으로 조회합니다. 적용된 정렬은 응답 pageable.sort에 실려 옵니다. " +
                    "가격순에서 가격제안(price null) 글은 맨 뒤에 오며, 그 구간의 커서는 cursorValue를 비우고 보냅니다."
    )
    public ResponseEntity<ApiResponse<CursorSlice<UsedProductSummaryResponseDto>>> getRegionProducts(
            @Parameter(description = "거래 지역 ID. 생략 시 내가 선택한 동네")
            @RequestParam(required = false) @Positive Long regionId,
            @Parameter(description = "제목·본문 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "카테고리 ID. 상위 카테고리를 지정하면 하위 카테고리 글도 함께 조회된다")
            @RequestParam(required = false) @Positive Long categoryId,
            @Parameter(description = "거래 유형 (FIXED·FREE·NEGOTIABLE)")
            @RequestParam(required = false) UsedProductPriceType priceType,
            @Parameter(description = "최소 가격. 지정하면 가격제안 글은 제외된다")
            @RequestParam(required = false)
            @DecimalMin(value = "0.00", message = "가격은 0 이상이어야 합니다")
            @Digits(integer = 8, fraction = 2, message = "가격은 정수 8자리, 소수 2자리까지 입력할 수 있습니다")
            BigDecimal minPrice,
            @Parameter(description = "최대 가격. 지정하면 가격제안 글은 제외된다")
            @RequestParam(required = false)
            @DecimalMin(value = "0.00", message = "가격은 0 이상이어야 합니다")
            @Digits(integer = 8, fraction = 2, message = "가격은 정수 8자리, 소수 2자리까지 입력할 수 있습니다")
            BigDecimal maxPrice,
            @Parameter(description = "거래 상태. 여러 번 보낼 수 있다. 거래완료를 숨기려면 SELLING·RESERVED")
            // 요소에 @NotNull이 없으면 ?status= 같은 빈 값이 null 원소로 변환돼
            // status IN (null)로 나가고, 잘못된 요청이 400 대신 조용히 0건으로 끝난다.
            @RequestParam(required = false) List<@NotNull UsedProductStatus> status,
            @Parameter(description = "직전 응답의 nextCursorValue. "
                    + "첫 페이지이거나 가격제안 글 구간이면 생략")
            @RequestParam(required = false) String cursorValue,
            @Parameter(description = "직전 응답의 nextCursorId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // 비회원도 둘러볼 수 있다 — 실제 거래(채팅)에서 지역 인증을 요구한다.
        Long viewerId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.getRegionProducts(
                        viewerId,
                        new UsedProductSearchRequestDto(
                                regionId, keyword, categoryId, priceType, minPrice, maxPrice, status),
                        cursorValue,
                        cursorId,
                        pageable)));
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
    @SecurityRequirements   // 전역 bearer 설정 해제 — 비회원도 조회할 수 있다
    @Operation(
            summary = "중고 게시글 상세 조회",
            description = "로그인 없이 조회할 수 있습니다. 삭제된 게시글은 조회되지 않고, " +
                    "관리자가 숨긴 게시글은 작성자 본인에게만 보입니다. 본인 글이 아니면 조회수가 1 증가합니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> getUsedProduct(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId
    ) {
        Long viewerId = SecurityUtil.getCurrentAccountIdOrNull();
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
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
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
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        usedProductService.delete(sellerId, usedProductId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 거래 상태 =====================

    @PostMapping("/{usedProductId}/reservation")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "예약 처리",
            description = "판매중인 게시글을 예약중으로 바꿉니다. 작성자 본인만 가능합니다. " +
                    "거래 상대(buyerId)는 선택이며, 지정하면 예약 취소 시 함께 해제됩니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> reserve(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @RequestBody(required = false) @Valid UsedProductTradePartnerRequestDto request
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(usedProductService.reserve(
                sellerId, usedProductId, request == null ? null : request.getBuyerId())));
    }

    @DeleteMapping("/{usedProductId}/reservation")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "예약 취소",
            description = "예약중인 게시글을 다시 판매중으로 되돌리고 지정했던 거래 상대를 해제합니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> cancelReservation(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedProductService.cancelReservation(sellerId, usedProductId)));
    }

    @PostMapping("/{usedProductId}/sold")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "판매완료 처리",
            description = "게시글을 판매완료로 바꿉니다. 예약을 거치지 않고 바로 완료할 수도 있습니다. " +
                    "여기서 확정된 거래 상대만 후기를 남길 수 있습니다. " +
                    "buyerId를 생략하면 예약 때 지정한 상대를 그대로 유지합니다."
    )
    public ResponseEntity<ApiResponse<UsedProductDetailResponseDto>> markSold(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @RequestBody(required = false) @Valid UsedProductTradePartnerRequestDto request
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(usedProductService.markSold(
                sellerId, usedProductId, request == null ? null : request.getBuyerId())));
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
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @Valid @RequestBody UsedProductImageUploadListRequestDto request
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
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @Parameter(description = "이미지 ID") @PathVariable @Positive Long imageId
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
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @Parameter(description = "이미지 ID") @PathVariable @Positive Long imageId
    ) {
        Long sellerId = SecurityUtil.getCurrentAccountId();
        usedProductImageService.changeThumbnail(sellerId, usedProductId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
