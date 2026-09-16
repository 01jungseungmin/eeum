package com.eeum.eeum.api.inquiry;

import com.eeum.eeum.application.inquiry.dto.request.InquiryAnswerCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.application.inquiry.service.AdminInquiryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.inquiry.enums.InquiryCategory;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
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

@Tag(name = "30. Admin - Inquiry", description = "관리자 문의 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @Operation(
            summary = "관리자 문의 목록 조회",
            description = "targetType=ADMIN 문의를 최신순으로 조회합니다. " +
                    "status/category/keyword로 필터링할 수 있으며, 미전달 시 전체 조회입니다. " +
                    "미답변 문의만 보려면 status=PENDING을 사용하세요."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InquiryResponseDto>>> getAdminInquiries(
            @Parameter(description = "상태 필터 (PENDING·ANSWERED·CLOSED)")
            @RequestParam(required = false) InquiryStatus status,
            @Parameter(description = "카테고리 필터 (STORE·ORDER·PAYMENT·RESERVATION·ACCOUNT·REPORT·ETC)")
            @RequestParam(required = false) InquiryCategory category,
            @Parameter(description = "제목·본문 검색어")
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminInquiryService.getAdminInquiries(status, category, keyword, pageable)));
    }

    @Operation(summary = "관리자 문의 상세 조회", description = "관리자 문의 상세와 답변 목록을 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryDetailResponseDto>> getAdminInquiryDetail(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminInquiryService.getAdminInquiryDetail(inquiryId)));
    }

    @Operation(
            summary = "관리자 문의 답변 작성",
            description = "ADMIN 문의에 관리자가 답변을 작성합니다. 답변 후 문의 상태가 ANSWERED로 변경됩니다."
    )
    @PostMapping("/{inquiryId}/answers")
    public ResponseEntity<ApiResponse<InquiryAnswerResponseDto>> answerInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerCreateRequestDto request
    ) {
        Long adminId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminInquiryService.answerInquiry(adminId, inquiryId, request)));
    }

    @Operation(
            summary = "관리자 문의 답변 수정",
            description = "관리자가 작성한 답변 본문을 정정합니다. 삭제는 제공하지 않으며, " +
                    "답변이 불필요해진 문의는 종료(close)를 사용하세요. " +
                    "수정 시 응답의 edited=true, modifiedAt이 갱신되고 알림은 재발송되지 않습니다."
    )
    @PatchMapping("/{inquiryId}/answers/{answerId}")
    public ResponseEntity<ApiResponse<InquiryAnswerResponseDto>> updateAnswer(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Parameter(description = "답변 ID") @PathVariable Long answerId,
            @Valid @RequestBody InquiryAnswerCreateRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminInquiryService.updateAnswer(inquiryId, answerId, request)));
    }

    // ===================== 문의 종료 / 재오픈 =====================

    @Operation(
            summary = "관리자 문의 강제 종료",
            description = "답변이 불필요하거나 스팸인 문의를 CLOSED로 전환합니다. " +
                    "종료된 문의에는 답변을 달 수 없습니다."
    )
    @PatchMapping("/{inquiryId}/close")
    public ResponseEntity<ApiResponse<Void>> closeInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        adminInquiryService.closeInquiry(inquiryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "관리자 문의 재오픈",
            description = "종료된 문의를 다시 엽니다. 상태는 PENDING으로 돌아가 미답변 목록에 다시 노출됩니다."
    )
    @PatchMapping("/{inquiryId}/reopen")
    public ResponseEntity<ApiResponse<Void>> reopenInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        adminInquiryService.reopenInquiry(inquiryId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
