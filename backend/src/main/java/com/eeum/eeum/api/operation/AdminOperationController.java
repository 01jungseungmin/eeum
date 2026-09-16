package com.eeum.eeum.api.operation;

import com.eeum.eeum.application.operation.dto.response.OperationFailureLogResponseDto;
import com.eeum.eeum.application.operation.dto.response.OperationSummaryResponseDto;
import com.eeum.eeum.application.operation.service.AdminOperationService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "20. Admin Operation", description = "관리자 운영 현황 (실패 이력 / 요약)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationController {

    private final AdminOperationService adminOperationService;

    @Operation(
            summary = "운영 실패 이력 목록",
            description = "결제 Webhook·환불·스케줄러·외부 API 실패를 한곳에서 조회합니다. " +
                    "category/from/to/keyword로 필터링할 수 있으며 미전달 시 전체 조회입니다. " +
                    "보존 기간은 3개월이며 이후 자동 삭제됩니다."
    )
    @GetMapping("/failures")
    public ResponseEntity<ApiResponse<Page<OperationFailureLogResponseDto>>> getFailures(
            @Parameter(description = "분류 필터 (PAYMENT_WEBHOOK·REFUND·SCHEDULER·EXTERNAL_API)")
            @RequestParam(required = false) OperationFailureCategory category,
            @Parameter(description = "조회 시작 시각 (ISO-8601)", example = "2026-08-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "조회 종료 시각 (ISO-8601)", example = "2026-08-31T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "작업명·에러코드·메시지 검색어")
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationService.getFailures(category, from, to, keyword, pageable)));
    }

    @Operation(
            summary = "운영 현황 요약",
            description = "미처리 신고 수, 미답변 관리자 문의 수, 최근 실패 건수(카테고리별)와 " +
                    "최근 실패 10건을 한 번에 반환합니다. 대시보드 상단 지표용입니다."
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OperationSummaryResponseDto>> getSummary(
            @Parameter(description = "실패 집계 구간(시간). 미전달 시 24시간", example = "24")
            @RequestParam(required = false) Integer hours
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminOperationService.getSummary(hours)));
    }
}
