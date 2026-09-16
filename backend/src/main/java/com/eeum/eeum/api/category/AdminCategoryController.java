package com.eeum.eeum.api.category;

import com.eeum.eeum.application.category.dto.request.CategoryCreateRequestDto;
import com.eeum.eeum.application.category.dto.request.CategoryOrderUpdateRequestDto;
import com.eeum.eeum.application.category.dto.request.CategoryUpdateRequestDto;
import com.eeum.eeum.application.category.dto.response.CategoryResponseDto;
import com.eeum.eeum.application.category.service.AdminCategoryService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.category.enums.CategoryType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "34. Admin - Category", description = "관리자 공통 카테고리 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @Operation(summary = "[관리자] 공통 카테고리 목록 조회", description = "비활성 카테고리를 포함하며 타입별 조회가 가능합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> getAllCategories(
            @RequestParam(required = false) CategoryType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminCategoryService.getAllCategories(type)));
    }

    @Operation(summary = "[관리자] 공통 카테고리 생성", description = "상위 카테고리 기준 최대 3단계까지 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponseDto>> createCategory(
            @Valid @RequestBody CategoryCreateRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminCategoryService.createCategory(request)));
    }

    @Operation(summary = "[관리자] 공통 카테고리 수정", description = "이름과 표시 순서를 수정합니다.")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponseDto>> updateCategory(
            @Parameter(description = "카테고리 ID") @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminCategoryService.updateCategory(categoryId, request)));
    }

    @Operation(summary = "[관리자] 공통 카테고리 순서 일괄 변경", description = "같은 타입·같은 부모의 전체 카테고리 ID를 원하는 순서대로 전달합니다.")
    @PatchMapping("/order")
    public ResponseEntity<ApiResponse<Void>> updateCategoryOrder(
            @Valid @RequestBody CategoryOrderUpdateRequestDto request
    ) {
        adminCategoryService.updateCategoryOrder(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 공통 카테고리 활성화")
    @PatchMapping("/{categoryId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateCategory(@PathVariable Long categoryId) {
        adminCategoryService.activateCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 공통 카테고리 비활성화", description = "사용자 화면 노출에서 제외하는 soft delete입니다.")
    @PatchMapping("/{categoryId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable Long categoryId) {
        adminCategoryService.deactivateCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 공통 카테고리 물리 삭제", description = "자식 카테고리나 참조 데이터가 없는 경우에만 삭제합니다.")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> hardDeleteCategory(@PathVariable Long categoryId) {
        adminCategoryService.hardDeleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
