package com.eeum.eeum.api.category;

import com.eeum.eeum.application.category.dto.response.PublicCategoryResponseDto;
import com.eeum.eeum.application.category.service.CategoryQueryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "12. Category", description = "사용자 카테고리 조회 API")
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @Operation(summary = "사용자 카테고리 목록 조회",
            description = "유형별 활성 카테고리만 표시 순서대로 조회합니다.")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicCategoryResponseDto>>> getCategories(
            @RequestParam CategoryType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(categoryQueryService.getActiveCategories(type)));
    }
}
