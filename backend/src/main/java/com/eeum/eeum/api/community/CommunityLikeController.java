package com.eeum.eeum.api.community;

import com.eeum.eeum.application.community.service.CommunityLikeService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
@Tag(name = "23. Community Like", description = "커뮤니티 게시글/댓글 좋아요 API")
public class CommunityLikeController {

    private final CommunityLikeService likeService;

    @PostMapping("/posts/{postId}/likes")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 좋아요")
    public ResponseEntity<ApiResponse<Void>> likePost(@PathVariable Long postId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        likeService.likePost(accountId, postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/posts/{postId}/likes")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "게시글 좋아요 취소")
    public ResponseEntity<ApiResponse<Void>> unlikePost(@PathVariable Long postId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        likeService.unlikePost(accountId, postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/comments/{commentId}/likes")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "댓글 좋아요")
    public ResponseEntity<ApiResponse<Void>> likeComment(@PathVariable Long commentId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        likeService.likeComment(accountId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/comments/{commentId}/likes")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "댓글 좋아요 취소")
    public ResponseEntity<ApiResponse<Void>> unlikeComment(@PathVariable Long commentId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        likeService.unlikeComment(accountId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
