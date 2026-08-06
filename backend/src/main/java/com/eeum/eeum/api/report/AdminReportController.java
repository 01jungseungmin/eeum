package com.eeum.eeum.api.report;

import com.eeum.eeum.application.report.dto.request.ReportReviewRequestDto;
import com.eeum.eeum.application.report.dto.response.ReportDetailResponseDto;
import com.eeum.eeum.application.report.dto.response.ReportResponseDto;
import com.eeum.eeum.application.report.service.AdminReportService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.report.enums.ReportStatus;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "33. Admin - Report", description = "관리자 신고 처리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "[관리자] 신고 목록 조회", description = "신고 전체 목록을 조회합니다. status 필터링 가능.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReportResponseDto>>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminReportService.getReports(status, pageable)));
    }

    @Operation(
            summary = "[관리자] 신고 상세 조회",
            description = "신고 기본 정보에 더해 신고자 정보, 신고 대상(가게/리뷰/게시글/댓글/사용자) 스냅샷, " +
                    "관리자 처리 내역을 함께 반환합니다. 신고 접수 후 대상이 삭제된 경우 target.exists=false로 반환됩니다."
    )
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponseDto>> getReportDetail(
            @Parameter(description = "신고 ID") @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminReportService.getReportDetail(reportId)));
    }

    @Operation(summary = "[관리자] 신고 검토 완료", description = "신고를 REVIEWED 상태로 처리합니다.")
    @PatchMapping("/{reportId}/review")
    public ResponseEntity<ApiResponse<ReportResponseDto>> reviewReport(
            @Parameter(description = "신고 ID") @PathVariable Long reportId,
            @Valid @RequestBody ReportReviewRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminReportService.reviewReport(reportId, request)));
    }

    @Operation(summary = "[관리자] 신고 기각", description = "신고를 DISMISSED 상태로 처리합니다.")
    @PatchMapping("/{reportId}/dismiss")
    public ResponseEntity<ApiResponse<ReportResponseDto>> dismissReport(
            @Parameter(description = "신고 ID") @PathVariable Long reportId,
            @Valid @RequestBody ReportReviewRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminReportService.dismissReport(reportId, request)));
    }
}
