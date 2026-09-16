package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.service.AdminChatService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "17. Admin Chat", description = "관리자 채팅 모니터링/모더레이션 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final AdminChatService adminChatService;

    @Operation(summary = "전체 채팅방 조회", description = "타입/활성여부/기간으로 채팅방을 필터링 조회합니다.")
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<Page<ChatRoomResponseDto>>> getAllRooms(
            ChatRoomAdminSearchDto condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminChatService.getAllRooms(condition, pageable)));
    }

    @Operation(summary = "채팅방 메시지 조회", description = "신고 처리 등을 위해 특정 채팅방의 메시지를 조회합니다.")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponseDto>>> getRoomMessages(
            @PathVariable Long roomId,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminChatService.getRoomMessages(roomId, pageable)));
    }

    @Operation(summary = "메시지 강제 삭제", description = "신고된 메시지를 강제로 Soft Delete 합니다.")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> forceDeleteMessage(@PathVariable Long messageId) {
        adminChatService.forceDeleteMessage(messageId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "채팅방 강제 비활성화", description = "문제가 된 채팅방을 강제로 비활성화합니다.")
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> forceDeactivateRoom(@PathVariable Long roomId) {
        adminChatService.forceDeactivateRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
