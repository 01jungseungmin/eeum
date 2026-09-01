package com.eeum.eeum.api.used;

import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedReviewUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewSummaryResponseDto;
import com.eeum.eeum.application.used.service.UsedReviewService;
import com.eeum.eeum.domain.used.repository.UsedReviewCursor;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                usedReviewService.create(reviewerId, usedProductId, request)));
    }

    @GetMapping("/sellers/{sellerId}/reviews")
    @Operation(
            summary = "판매자가 받은 후기 목록",
            description = "판매자 평판이므로 로그인 없이 볼 수 있습니다. 작성 최신순 커서 페이징(Slice)입니다. " +
                    "첫 페이지는 커서 없이 요청하고, 다음 페이지는 받은 마지막 항목의 createdAt과 " +
                    "usedReviewId를 cursorCreatedAt·cursorId에 담아 보냅니다(둘 중 하나만 보내면 400). " +
                    "페이지 번호를 쓰지 않는 이유는 스크롤 도중 새 후기가 등록되면 목록이 밀려 " +
                    "경계 항목이 중복되거나 누락되기 때문입니다. " +
                    "게시글이 삭제·숨김된 후기도 그대로 보이며, 그 경우 usedProductVisible이 false이고 " +
                    "게시글 제목은 비어 있습니다. 단, 거래 당사자에게는 제목이 그대로 보입니다 — " +
                    "후기를 쓴 본인과 그 게시글의 판매자(즉 자기 평판 목록을 보는 판매자 본인)입니다."
    )
    public ResponseEntity<ApiResponse<Slice<UsedReviewResponseDto>>> getSellerReviews(
            @Parameter(description = "판매자 계정 ID") @PathVariable @Positive Long sellerId,
            @Parameter(description = "직전 페이지 마지막 항목의 createdAt. 첫 페이지면 생략")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @Parameter(description = "직전 페이지 마지막 항목의 usedReviewId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 50)")
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size
    ) {
        // 비회원도 볼 수 있는 경로라 뷰어가 없을 수 있다.
        Long viewerId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(usedReviewService.getSellerReviews(
                sellerId, viewerId, UsedReviewCursor.ofNullable(cursorCreatedAt, cursorId), size)));
    }

    @GetMapping("/sellers/{sellerId}/reviews/summary")
    @Operation(
            summary = "판매자 평판 요약",
            description = "받은 후기 수와 평균 별점입니다. 판매자 평판이므로 로그인 없이 볼 수 있습니다. " +
                    "게시글이 삭제·숨김된 후기도 집계에 포함됩니다 — 나쁜 후기가 달린 글을 지워 " +
                    "평균을 올리는 것을 막기 위해서입니다. " +
                    "후기가 없으면 reviewCount는 0이고 averageRating은 null입니다(0.0이 아닙니다)."
    )
    public ResponseEntity<ApiResponse<UsedReviewSummaryResponseDto>> getSellerReviewSummary(
            @Parameter(description = "판매자 계정 ID") @PathVariable @Positive Long sellerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                usedReviewService.getSellerReviewSummary(sellerId)));
    }

    @GetMapping("/reviews/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "내가 쓴 후기 목록",
            description = "작성 최신순 커서 페이징(Slice)입니다. 첫 페이지는 커서 없이 요청하고, " +
                    "다음 페이지는 받은 마지막 항목의 createdAt과 usedReviewId를 " +
                    "cursorCreatedAt·cursorId에 담아 보냅니다(둘 중 하나만 보내면 400). " +
                    "내가 쓴 후기이므로 게시글이 삭제·숨김돼도 제목은 그대로 보입니다."
    )
    public ResponseEntity<ApiResponse<Slice<UsedReviewResponseDto>>> getMyReviews(
            @Parameter(description = "직전 페이지 마지막 항목의 createdAt. 첫 페이지면 생략")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @Parameter(description = "직전 페이지 마지막 항목의 usedReviewId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 50)")
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size
    ) {
        Long reviewerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(usedReviewService.getMyReviews(
                reviewerId, UsedReviewCursor.ofNullable(cursorCreatedAt, cursorId), size)));
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
