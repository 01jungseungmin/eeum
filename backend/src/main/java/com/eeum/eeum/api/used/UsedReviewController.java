package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedReviewUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewResponseDto;
import com.eeum.eeum.application.used.service.UsedReviewService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "22. Used Review", description = "중고거래 후기 API")
@Validated
@RestController
@RequestMapping("/used")
@RequiredArgsConstructor
public class UsedReviewController {

    private final UsedReviewService usedReviewService;

    @PostMapping("/{usedProductId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고거래 후기 작성",
            description = "거래가 완료(SOLD)됐고 본인이 그 거래의 구매자로 지정된 경우에만 작성할 수 있습니다. " +
                    "판매자는 후기를 쓸 수 없습니다. 한 거래에 후기는 하나만 남길 수 있습니다."
    )
    public ResponseEntity<ApiResponse<UsedReviewResponseDto>> createReview(
            @Parameter(description = "게시글 ID") @PathVariable @Positive Long usedProductId,
            @RequestBody @Valid UsedReviewCreateRequestDto request
    ) {
        Long reviewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedReviewService.create(reviewerId, usedProductId, request)));
    }

    @GetMapping("/sellers/{sellerId}/reviews")
    @Operation(
            summary = "판매자가 받은 후기 목록",
            description = "판매자 평판이므로 로그인 없이 볼 수 있습니다. 작성 최신순 무한 스크롤(Slice)입니다. " +
                    "게시글이 삭제·숨김된 후기도 그대로 보이며, 그 경우 게시글 제목은 비어 있고 " +
                    "usedProductVisible이 false입니다."
    )
    public ResponseEntity<ApiResponse<Slice<UsedReviewResponseDto>>> getSellerReviews(
            @Parameter(description = "판매자 계정 ID") @PathVariable @Positive Long sellerId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                usedReviewService.getSellerReviews(sellerId, pageable)));
    }

    @GetMapping("/reviews/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "내가 쓴 후기 목록",
            description = "작성 최신순 무한 스크롤(Slice)입니다."
    )
    public ResponseEntity<ApiResponse<Slice<UsedReviewResponseDto>>> getMyReviews(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long reviewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedReviewService.getMyReviews(reviewerId, pageable)));
    }

    @PatchMapping("/reviews/{usedReviewId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고거래 후기 수정",
            description = "작성자 본인만 수정할 수 있습니다."
    )
    public ResponseEntity<ApiResponse<UsedReviewResponseDto>> updateReview(
            @Parameter(description = "후기 ID") @PathVariable @Positive Long usedReviewId,
            @RequestBody @Valid UsedReviewUpdateRequestDto request
    ) {
        Long reviewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                usedReviewService.update(reviewerId, usedReviewId, request)));
    }

    @DeleteMapping("/reviews/{usedReviewId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "중고거래 후기 삭제",
            description = "작성자 본인만 삭제할 수 있습니다. 삭제하면 복구할 수 없습니다."
    )
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @Parameter(description = "후기 ID") @PathVariable @Positive Long usedReviewId
    ) {
        Long reviewerId = SecurityUtil.getCurrentAccountId();
        usedReviewService.delete(reviewerId, usedReviewId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
