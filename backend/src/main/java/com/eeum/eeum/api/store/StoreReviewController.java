package com.eeum.eeum.api.store;

import com.eeum.eeum.application.store.dto.request.StoreReviewCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewImageAddRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewUpdateRequestDto;
import com.eeum.eeum.application.store.dto.response.StoreReviewDetailResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreReviewResponseDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
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

@Tag(name = "12. StoreReview", description = "상점 리뷰 / 평점 / 사장 답글 API")
@RestController
@RequestMapping("/stores/{storeId}/reviews")
@RequiredArgsConstructor
public class StoreReviewController {

    private final StoreReviewService storeReviewService;

    // ===================== 공개 조회 (비회원 포함) =====================

    @Operation(summary = "상점 리뷰 목록 조회", description = "상점의 리뷰 목록을 최신순으로 조회합니다. 비회원 접근 가능.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StoreReviewResponseDto>>> getReviews(
            @PathVariable Long storeId,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getReviews(storeId, pageable)));
    }

    @Operation(summary = "상점 리뷰 상세 조회", description = "리뷰 단건 상세 정보(이미지, 답글 포함)를 조회합니다. 비회원 접근 가능.")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<StoreReviewDetailResponseDto>> getReviewDetail(
            @PathVariable Long storeId,
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getReviewDetail(storeId, reviewId)));
    }

    // ===================== 리뷰 작성/수정/삭제 (로그인 필수) =====================

    @Operation(summary = "리뷰 작성", description = "거래 완료(COMPLETED) 주문 기준 리뷰를 작성합니다. 1주문 1리뷰.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreReviewResponseDto>> createReview(
            @PathVariable Long storeId,
            @RequestBody @Valid StoreReviewCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                storeReviewService.createReview(accountId, storeId, request)));
    }

    @Operation(summary = "리뷰 수정", description = "본인이 작성한 리뷰의 별점과 내용을 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<StoreReviewResponseDto>> updateReview(
            @PathVariable Long storeId,
            @PathVariable Long reviewId,
            @RequestBody @Valid StoreReviewUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.updateReview(accountId, storeId, reviewId, request)));
    }

    @Operation(summary = "리뷰 삭제", description = "본인이 작성한 리뷰를 삭제합니다. 연관 이미지·답글도 함께 삭제됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long storeId,
            @PathVariable Long reviewId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeReviewService.deleteReview(accountId, storeId, reviewId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 리뷰 이미지 관리 (로그인 필수) =====================

    @Operation(summary = "리뷰 이미지 추가", description = "리뷰에 이미지 URL을 추가합니다. 1회 최대 10장 추가 가능, 리뷰당 총 최대 10장.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{reviewId}/images")
    public ResponseEntity<ApiResponse<StoreReviewResponseDto>> addReviewImages(
            @PathVariable Long storeId,
            @PathVariable Long reviewId,
            @RequestBody @Valid StoreReviewImageAddRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                storeReviewService.addReviewImages(
                        accountId,
                        storeId,
                        reviewId,
                        request.getImageUrls()
                )
        ));
    }

    @Operation(summary = "리뷰 이미지 삭제", description = "리뷰 이미지를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{reviewId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteReviewImage(
            @PathVariable Long storeId,
            @PathVariable Long reviewId,
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeReviewService.deleteReviewImage(accountId, storeId, reviewId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "리뷰 대표 이미지 지정", description = "리뷰의 대표(썸네일) 이미지를 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{reviewId}/images/{imageId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> setReviewImageThumbnail(
            @PathVariable Long storeId,
            @PathVariable Long reviewId,
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        storeReviewService.setReviewImageThumbnail(accountId, storeId, reviewId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 사장 답글 (ROLE_OWNER) =====================
}