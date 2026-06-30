package com.eeum.eeum.api.inquiry;

import com.eeum.eeum.application.inquiry.dto.request.InquiryAnswerCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryAnswerResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.application.inquiry.service.AdminInquiryService;
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

@Tag(name = "30. Admin - Inquiry", description = "관리자 문의 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @Operation(summary = "관리자 문의 목록 조회", description = "targetType=ADMIN 문의 전체를 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InquiryResponseDto>>> getAdminInquiries(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminInquiryService.getAdminInquiries(pageable)));
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
}
