package com.eeum.eeum.api.region;

import com.eeum.eeum.application.region.service.LocationSyncService;
import com.eeum.eeum.application.region.service.RegionService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Location", description = "관리자 지역 데이터 동기화 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLocationController {

    private final LocationSyncService locationSyncService;
    private final RegionService regionService;

    @Operation(summary = "행정동 지역 데이터 동기화",
            description = "외부 행정동 API에서 지역 데이터를 가져와 DB에 저장합니다.")
    @PostMapping("/sync/regions")
    public ResponseEntity<ApiResponse<String>> syncRegions() {
        regionService.saveRegion();
        return ResponseEntity.ok(ApiResponse.success("지역 데이터 동기화 완료"));
    }

    @Operation(summary = "Location 좌표 동기화",
            description = "지역 데이터 중 좌표가 없는 항목을 카카오 지도 API로 보완합니다.size 크기를 키우면 더 많은 데이터의 좌표가 저장됩니다")
    @PostMapping("/sync/coordinates")
    public ResponseEntity<ApiResponse<String>> syncLocations(
            @RequestParam(defaultValue = "100") int size
    ) {
        locationSyncService.syncMissingLocations(size);
        return ResponseEntity.ok(ApiResponse.success("Location 좌표 동기화 완료"));
    }
}