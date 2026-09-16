package com.eeum.eeum.api.report;

import com.eeum.eeum.application.report.dto.request.ReportCreateRequestDto;
import com.eeum.eeum.application.report.dto.response.MyReportResponseDto;
import com.eeum.eeum.application.report.service.ReportService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "32. Report", description = "신고 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "신고 접수", description = "대상(targetType, targetId)에 대해 신고를 접수합니다. 동일 대상 중복 신고 불가.")
    @PostMapping
    public ResponseEntity<ApiResponse<MyReportResponseDto>> createReport(
            @Valid @RequestBody ReportCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reportService.createReport(accountId, request)));
    }

    @Operation(summary = "내 신고 목록 조회", description = "본인이 접수한 신고 목록을 최신순으로 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Slice<MyReportResponseDto>>> getMyReports(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(reportService.getMyReports(accountId, pageable)));
    }

    @Operation(summary = "내 신고 상세 조회", description = "본인이 접수한 신고 상세를 조회합니다.")
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<MyReportResponseDto>> getMyReportDetail(
            @Parameter(description = "신고 ID") @PathVariable @Positive Long reportId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(reportService.getMyReportDetail(accountId, reportId)));
    }
}
