package com.eeum.eeum.api.community;

import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.application.community.service.AdminCommunityPostService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "33. Admin - Community Post", description = "관리자 커뮤니티 게시글 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/admin/community/posts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunityPostController {

    private final AdminCommunityPostService adminCommunityPostService;

    @Operation(summary = "[관리자] 커뮤니티 게시글 목록 조회", description = "숨김 게시글을 포함해 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommunityPostSummaryResponseDto>>> getPosts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminCommunityPostService.getPosts(pageable)));
    }

    @Operation(summary = "[관리자] 커뮤니티 게시글 상세 조회", description = "숨김 게시글도 조회할 수 있습니다.")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponseDto>> getDetail(
            @PathVariable @Positive Long postId) {
        return ResponseEntity.ok(ApiResponse.success(adminCommunityPostService.getDetail(postId)));
    }

    @Operation(summary = "[관리자] 커뮤니티 게시글 숨김", description = "게시글을 일반 사용자 목록과 상세에서 숨깁니다.")
    @PatchMapping("/{postId}/hide")
    public ResponseEntity<ApiResponse<Void>> hide(@PathVariable @Positive Long postId) {
        adminCommunityPostService.hide(postId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "[관리자] 커뮤니티 게시글 숨김 해제")
    @PatchMapping("/{postId}/show")
    public ResponseEntity<ApiResponse<Void>> show(@PathVariable @Positive Long postId) {
        adminCommunityPostService.show(postId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
