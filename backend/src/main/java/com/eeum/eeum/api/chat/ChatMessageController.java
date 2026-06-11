package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatUnreadCountResponseDto;
import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "16. Chat Message", description = "채팅 메시지 API (REST — WebSocket 도입 전)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @Operation(summary = "메시지 목록 조회", description = "최신순으로 메시지를 페이징 조회합니다. 삭제된 메시지는 '삭제된 메시지입니다'로 표시됩니다.")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponseDto>>> getMessages(
            @PathVariable Long roomId,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.getMessages(accountId, roomId, pageable)));
    }

    @Operation(summary = "텍스트 메시지 발송", description = "REST 폴백 경로로 텍스트 메시지를 발송합니다.")
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> sendMessage(
            @PathVariable Long roomId,
            @RequestBody @Valid ChatMessageSendRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.sendMessage(accountId, roomId, request)));
    }

    @Operation(summary = "이미지 메시지 발송", description = "S3 업로드 완료된 이미지 URL로 이미지 메시지를 발송합니다.")
    @PostMapping("/rooms/{roomId}/messages/image")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> sendImageMessage(
            @PathVariable Long roomId,
            @RequestBody @Valid ChatImageMessageSendRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.sendImageMessage(accountId, roomId, request)));
    }

    @Operation(summary = "메시지 삭제", description = "본인이 발송한 메시지를 Soft Delete 합니다.")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable Long messageId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatMessageService.deleteMessage(accountId, messageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "전체 안 읽은 채팅 수", description = "채팅 탭 배지용 안 읽은 메시지 총합을 반환합니다.")
    @GetMapping("/messages/unread/count")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponseDto>> countUnread() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.countUnread(accountId)));
    }
}
