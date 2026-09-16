package com.eeum.eeum.api.search;

import com.eeum.eeum.application.search.dto.response.PopularSearchKeywordResponseDto;
import com.eeum.eeum.application.search.enums.PopularSearchScope;
import com.eeum.eeum.application.search.service.PopularSearchService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@Tag(name = "12. Search", description = "인기 검색어 API")
@RestController
@RequestMapping("/searches")
@RequiredArgsConstructor
public class PopularSearchController {

    private final PopularSearchService popularSearchService;

    @GetMapping("/popular-keywords")
    @SecurityRequirements
    @Operation(summary = "인기 검색어 조회", description = "최근 24시간 기준 인기 검색어를 조회합니다. 점수는 노출하지 않습니다.")
    public ResponseEntity<ApiResponse<List<PopularSearchKeywordResponseDto>>> getPopularKeywords(
            @RequestParam(defaultValue = "ALL") PopularSearchScope scope,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(popularSearchService.getPopularKeywords(scope, limit)));
    }
}
