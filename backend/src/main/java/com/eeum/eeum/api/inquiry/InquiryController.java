package com.eeum.eeum.api.inquiry;

import com.eeum.eeum.application.inquiry.dto.request.InquiryCreateRequestDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryDetailResponseDto;
import com.eeum.eeum.application.inquiry.dto.response.InquiryResponseDto;
import com.eeum.eeum.application.inquiry.service.InquiryService;
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

@Tag(name = "25. Inquiry", description = "문의 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/inquiries")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(
            summary = "문의 작성",
            description = """
                    상점 문의(STORE) 또는 관리자 문의(ADMIN)를 작성합니다.
                    - STORE 문의: storeId 필수
                    - ADMIN 문의: storeId 불필요
                    USER, OWNER 모두 작성 가능합니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponseDto>> createInquiry(
            @Valid @RequestBody InquiryCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(inquiryService.createInquiry(accountId, request)));
    }

    @Operation(summary = "내 문의 목록 조회", description = "본인이 작성한 문의 목록을 최신순으로 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<InquiryResponseDto>>> getMyInquiries(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(inquiryService.getMyInquiries(accountId, pageable)));
    }

    @Operation(summary = "내 문의 상세 조회", description = "본인이 작성한 문의 상세와 답변 목록을 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryDetailResponseDto>> getMyInquiryDetail(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(inquiryService.getMyInquiryDetail(accountId, inquiryId)));
    }
}
