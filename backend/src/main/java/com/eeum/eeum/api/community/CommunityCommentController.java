package com.eeum.eeum.api.community;

import com.eeum.eeum.application.community.dto.request.CommunityCommentCreateRequestDto;
import com.eeum.eeum.application.community.dto.request.CommunityCommentUpdateRequestDto;
import com.eeum.eeum.application.community.dto.response.CommunityCommentResponseDto;
import com.eeum.eeum.application.community.service.CommunityCommentService;
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

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
@Tag(name = "22. Community Comment", description = "커뮤니티 댓글/대댓글 API")
public class CommunityCommentController {

    private final CommunityCommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "댓글 목록 조회")
    public ResponseEntity<ApiResponse<Page<CommunityCommentResponseDto>>> getComments(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(commentService.getComments(postId, accountId, pageable)));
    }

    @PostMapping("/posts/{postId}/comments")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "댓글 작성")
    public ResponseEntity<ApiResponse<CommunityCommentResponseDto>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommunityCommentCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(commentService.createComment(accountId, postId, request)));
    }

    @GetMapping("/comments/{commentId}/replies")
    @Operation(summary = "대댓글 목록 조회")
    public ResponseEntity<ApiResponse<Page<CommunityCommentResponseDto>>> getReplies(
            @PathVariable Long commentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountIdOrNull();
        return ResponseEntity.ok(ApiResponse.success(commentService.getReplies(accountId, commentId, pageable)));
    }

    @PostMapping("/comments/{commentId}/replies")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "대댓글 작성")
    public ResponseEntity<ApiResponse<CommunityCommentResponseDto>> createReply(
            @PathVariable Long commentId,
            @Valid @RequestBody CommunityCommentCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(commentService.createReply(accountId, commentId, request)));
    }

    @PatchMapping("/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "댓글/대댓글 수정")
    public ResponseEntity<ApiResponse<CommunityCommentResponseDto>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommunityCommentUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(commentService.updateComment(accountId, commentId, request)));
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "댓글/대댓글 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        commentService.deleteComment(accountId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
