package com.eeum.eeum.api.region;

import com.eeum.eeum.application.region.dto.response.RegionSearchResponseDto;
import com.eeum.eeum.application.region.service.RegionSearchService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "04. Region", description = "지역 검색 API")
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionSearchController {

    private final RegionSearchService regionSearchService;

    @Operation(
            summary = "지역 키워드 검색",
            description = "시/도, 시/군/구, 읍/면/동 이름으로 지역을 검색합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RegionSearchResponseDto>>> searchRegions(
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                regionSearchService.searchByKeyword(keyword)
        ));
    }

    @Operation(
            summary = "현재 위치 기반 근처 지역 조회",
            description = "현재 GPS 좌표 기준 반경 5km 내 지역을 가까운 순으로 조회합니다."
    )
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<RegionSearchResponseDto>>> getNearbyRegions(
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                regionSearchService.findNearby(latitude, longitude)
        ));
    }
}