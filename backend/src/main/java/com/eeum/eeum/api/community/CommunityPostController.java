package com.eeum.eeum.api.community;

import com.eeum.eeum.application.community.dto.request.CommunityPostCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityPostUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityImageResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostDetailResponseDto;
import com.eeum.eeum.application.community.dto.response.CommunityPostSummaryResponseDto;
import com.eeum.eeum.application.community.service.CommunityPostImageService;
import com.eeum.eeum.application.community.service.CommunityPostService;
import com.eeum.eeum.common.dto.request.ImageUploadListRequestDto;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/community/posts")
@RequiredArgsConstructor
@Tag(name = "21. Community Post", description = "커뮤니티 게시글 API")
public class CommunityPostController {

    private final CommunityPostService postService;
    private final CommunityPostImageService postImageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 목록 조회", description = "내 지역 게시글 목록을 조회합니다. keyword로 제목/내용을 검색할 수 있습니다.")
    public ResponseEntity<ApiResponse<Page<CommunityPostSummaryResponseDto>>> getPosts(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                postService.getPosts(accountId, keyword, pageable)
        ));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내가 작성한 게시글 목록 조회")
    public ResponseEntity<ApiResponse<Page<CommunityPostSummaryResponseDto>>> getMyPosts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();

        return ResponseEntity.ok(ApiResponse.success(
                postService.getMyPosts(accountId, pageable)
        ));
    }

    @GetMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 상세 조회")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponseDto>> getPost(
            @PathVariable Long postId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(postService.getPost(accountId, postId)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 작성")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponseDto>> createPost(
            @Valid @RequestBody CommunityPostCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(postService.createPost(accountId, request)));
    }

    @Operation(summary = "게시글 이미지 등록 (최대 20장)")
    @PostMapping("/{communityPostId}/images")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<CommunityImageResponseDto>>> addCommunityImages(
            @PathVariable Long communityPostId,
            @Valid @RequestBody ImageUploadListRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                postImageService.addImages(accountId, communityPostId, request)
        ));
    }

    @Operation(summary = "게시글 이미지 삭제")
    @DeleteMapping("/{communityPostId}/images/{imageId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> deleteCommunityImages(
            @PathVariable Long communityPostId,
            @PathVariable Long imageId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        postImageService.deleteImage(accountId, communityPostId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 수정")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponseDto>> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody CommunityPostUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(postService.updatePost(accountId, postId, request)));
    }

    @DeleteMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 삭제")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        postService.deletePost(accountId, postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
