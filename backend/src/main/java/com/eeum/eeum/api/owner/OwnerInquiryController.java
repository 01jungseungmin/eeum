package com.eeum.eeum.api.owner;

import com.eeum.eeum.application.inquiry.dto.request.InquiryAnswerCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.application.inquiry.service.OwnerInquiryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/owner/stores/me/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerInquiryController {

    private final OwnerInquiryService ownerInquiryService;

    @Operation(summary = "내 가게 문의 목록 조회", description = "내 가게로 들어온 문의 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InquiryResponseDto>>> getStoreInquiries(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(ownerInquiryService.getStoreInquiries(ownerId, pageable)));
    }

    @Operation(summary = "내 가게 문의 상세 조회", description = "내 가게에 들어온 문의 상세와 답변 목록을 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryDetailResponseDto>> getStoreInquiryDetail(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(ownerInquiryService.getStoreInquiryDetail(ownerId, inquiryId)));
    }

    @Operation(
            summary = "내 가게 문의 답변 작성",
            description = "내 가게에 들어온 STORE 문의에 답변을 작성합니다. 답변 후 문의 상태가 ANSWERED로 변경됩니다."
    )
    @PostMapping("/{inquiryId}/answers")
    public ResponseEntity<ApiResponse<InquiryAnswerResponseDto>> answerInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerCreateRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ownerInquiryService.answerInquiry(ownerId, inquiryId, request)));
    }

    @Operation(
            summary = "내 가게 문의 답변 수정",
            description = "내 가게 문의에 단 답변 본문을 정정합니다. 삭제는 제공하지 않습니다. " +
                    "수정 시 응답의 edited=true, modifiedAt이 갱신되며 알림은 재발송되지 않습니다."
    )
    @PatchMapping("/{inquiryId}/answers/{answerId}")
    public ResponseEntity<ApiResponse<InquiryAnswerResponseDto>> updateAnswer(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Parameter(description = "답변 ID") @PathVariable Long answerId,
            @Valid @RequestBody InquiryAnswerCreateRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                ownerInquiryService.updateAnswer(ownerId, inquiryId, answerId, request)));
    }
}
