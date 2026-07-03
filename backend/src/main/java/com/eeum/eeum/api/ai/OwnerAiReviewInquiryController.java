package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiComplaintDraftRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiMarketingDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiMarketingDraftResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiReviewInquiryResponseDto;
import com.eeum.eeum.application.ai.service.AiMarketingService;
import com.eeum.eeum.application.ai.service.AiReviewInquiryService;
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

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiReviewInquiryController {

    private final AiReviewInquiryService aiReviewInquiryService;
    private final AiMarketingService aiMarketingService;

    @Operation(summary = "리뷰/문의 자동 대응 현황 조회",
            description = "최근 2주 반복 불만 키워드, 미답변 리뷰/문의 목록을 조회합니다.")
    @GetMapping("/review-inquiries")
    public ResponseEntity<ApiResponse<AiReviewInquiryResponseDto>> getOverview() {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiReviewInquiryService.getOverview(ownerId)));
    }

    @Operation(summary = "리뷰 답글 초안 생성", description = "리뷰 평점에 맞는 답글 초안을 생성합니다. (Basic 이상, 월 사용량 카운트)")
    @PostMapping("/reviews/{reviewId}/reply-draft")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> createReviewReplyDraft(
            @Parameter(description = "리뷰 ID") @PathVariable Long reviewId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiReviewInquiryService.createReviewReplyDraft(ownerId, reviewId)));
    }

    @Operation(summary = "문의 답변 초안 생성", description = "내 가게 문의에 대한 답변 초안을 생성합니다. (Basic 이상, 월 사용량 카운트)")
    @PostMapping("/inquiries/{inquiryId}/reply-draft")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> createInquiryReplyDraft(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiReviewInquiryService.createInquiryReplyDraft(ownerId, inquiryId)));
    }

    @Operation(summary = "반복 불만 대응 문구 생성", description = "반복 불만 키워드에 대한 대응 문구를 생성합니다. (Basic 이상, 월 사용량 카운트)")
    @PostMapping("/review-inquiries/complaint-draft")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> createComplaintDraft(
            @Valid @RequestBody AiComplaintDraftRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiReviewInquiryService.createComplaintDraft(ownerId, request)));
    }

    @Operation(summary = "리뷰/문의 기반 공지 초안 생성",
            description = "공지 등록용 초안을 생성합니다. 발송 채널은 KAKAO_ALERT / APP_PUSH / STORE_NOTICE 3종만 허용됩니다.")
    @PostMapping("/review-inquiries/notice-draft")
    public ResponseEntity<ApiResponse<AiMarketingDraftResponseDto>> createNoticeDraft(
            @Valid @RequestBody AiMarketingDraftRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiMarketingService.createNoticeDraft(ownerId, request)));
    }
}
