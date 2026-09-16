package com.eeum.eeum.api.store;

import com.eeum.eeum.application.store.dto.request.StoreReviewReplyRequestDto;
import com.eeum.eeum.application.store.dto.response.OwnerStoreReviewDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.OwnerStoreReviewResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreReviewReplyResponseDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
import com.eeum.eeum.application.store.service.StoreService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "06. Owner - Store", description = "사장 상점 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/stores/me/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerStoreReviewController {

    private final StoreReviewService storeReviewService;
    private final StoreService storeService;

    @Operation(summary = "상점 리뷰 전체 조회", description = "내 상점에 작성된 리뷰 목록을 조회합니다. 답글 작성 여부 포함.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OwnerStoreReviewResponseDto>>> getReviews(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        Long storeId = storeService.getOwnerStoreId(accountId);
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getReviewsForOwner(accountId, storeId, pageable)));
    }

    @Operation(summary = "상점 리뷰 상세", description = "내 상점의 특정 리뷰 상세(이미지, 답글 포함)를 조회합니다.")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<OwnerStoreReviewDetailResponseDto>> getReviewDetail(
            @PathVariable Long reviewId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        Long storeId = storeService.getOwnerStoreId(accountId);
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getReviewDetailForOwner(accountId, storeId, reviewId)));
    }

    @Operation(summary = "리뷰 답글 작성", description = "상점 사장이 리뷰에 답글을 작성합니다. 리뷰당 1개만 허용.")
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<StoreReviewReplyResponseDto>> createReply(
            @PathVariable Long reviewId,
            @RequestBody @Valid StoreReviewReplyRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        Long storeId = storeService.getOwnerStoreId(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                storeReviewService.createReply(accountId, storeId, reviewId, request)));
    }

    @Operation(summary = "리뷰 답글 수정", description = "상점 사장이 작성한 답글을 수정합니다.")
    @PatchMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<StoreReviewReplyResponseDto>> updateReply(
            @PathVariable Long reviewId,
            @RequestBody @Valid StoreReviewReplyRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        Long storeId = storeService.getOwnerStoreId(accountId);
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.updateReply(accountId, storeId, reviewId, request)));
    }

    @Operation(summary = "리뷰 답글 삭제", description = "상점 사장이 작성한 답글을 삭제합니다.")
    @DeleteMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<Void>> deleteReply(
            @PathVariable Long reviewId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        Long storeId = storeService.getOwnerStoreId(accountId);
        storeReviewService.deleteReply(accountId, storeId, reviewId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
