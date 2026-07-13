package com.eeum.eeum.api.external;

import com.eeum.eeum.application.external.dto.ExternalImportHistoryResponseDto;
import com.eeum.eeum.application.external.service.ExternalDataImportService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Tag(name = "11. Admin - External Data", description = "관리자 공공데이터 파일 Import API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/external-data")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminExternalDataController {

    private final ExternalDataImportService externalDataImportService;

    @Operation(summary = "용도별 전력사용량 파일 Import",
            description = "한국전력공사_용도별 전력사용량(15101311) CSV를 내부 DB에 적재합니다. 같은 기준월 재적재 시 교체됩니다.")
    @PostMapping(value = "/import/electric-usage-type", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExternalImportHistoryResponseDto>> importElectricUsageType(
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "기관 데이터 기준일 (선택)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceUpdatedAt
    ) {
        return ResponseEntity.ok(ApiResponse.success(ExternalImportHistoryResponseDto.from(
                externalDataImportService.importElectricUsageType(file, sourceUpdatedAt))));
    }

    @Operation(summary = "건물에너지 전기에너지 파일 Import",
            description = "국토교통부_건물에너지 전기에너지(15054214) CSV를 법정동/월 단위로 집계 적재합니다. "
                    + "대용량 대비 sigunguFilter로 서비스 지역만 적재할 수 있습니다.")
    @PostMapping(value = "/import/building-electric-energy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExternalImportHistoryResponseDto>> importBuildingElectricEnergy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceUpdatedAt,
            @Parameter(description = "적재할 시군구 필터 (예: 마포구)") @RequestParam(required = false) String sigunguFilter
    ) {
        return ResponseEntity.ok(ApiResponse.success(ExternalImportHistoryResponseDto.from(
                externalDataImportService.importBuildingElectricEnergy(file, sourceUpdatedAt, sigunguFilter))));
    }

    @Operation(summary = "공공데이터 Import 이력 조회")
    @GetMapping("/import-history")
    public ResponseEntity<ApiResponse<Page<ExternalImportHistoryResponseDto>>> getImportHistory(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                externalDataImportService.getImportHistory(pageable)
                        .map(ExternalImportHistoryResponseDto::from)));
    }
}
