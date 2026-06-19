package com.eeum.eeum.api.store;

import com.eeum.eeum.application.store.dto.response.MyReviewResponseDto;
import com.eeum.eeum.application.store.service.StoreReviewService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.store.enums.StoreReviewType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "12. StoreReview", description = "상점 리뷰 / 평점 / 사장 답글 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class MyReviewController {

    private final StoreReviewService storeReviewService;

    @Operation(summary = "내 리뷰 목록 조회", description = "내가 작성한 주문 리뷰 / 방문 예약 리뷰를 최신순으로 조회합니다. type 파라미터로 필터링 가능 (ORDER, RESERVATION).")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<MyReviewResponseDto>>> getMyReviews(
            @Parameter(description = "리뷰 타입 필터 (미지정 시 전체)")
            @RequestParam(required = false) StoreReviewType type,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                storeReviewService.getMyReviews(accountId, type, pageable)));
    }
}
