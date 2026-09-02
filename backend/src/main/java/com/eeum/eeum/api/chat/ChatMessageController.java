package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatUnreadCountResponseDto;
import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Tag(name = "16. Chat Message", description = "채팅 메시지 API (텍스트/이미지 발송은 WebSocket 권장, REST는 폴백 경로)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @Operation(summary = "메시지 목록 조회",
               description = "최신순 커서 페이징입니다. 첫 페이지는 커서 없이 요청하고(최근 50개), "
                       + "과거 메시지는 직전 응답의 nextCursorValue·nextCursorId를 "
                       + "cursorValue·cursorId에 그대로 담아 보냅니다(둘 중 하나만 보내면 400). "
                       + "커서에 발신 시각과 메시지 ID를 함께 담는 이유는, 시각만 쓰면 같은 시각에 "
                       + "저장된 메시지가 페이지 경계에서 영구히 누락되기 때문입니다. "
                       + "다음 페이지가 없으면 nextCursor 값들은 null입니다. "
                       + "삭제된 메시지는 '삭제된 메시지입니다'로 표시됩니다.")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<CursorSlice<ChatMessageResponseDto>>> getMessages(
            @PathVariable @Positive Long roomId,
            @Parameter(description = "직전 응답의 nextCursorValue. 첫 페이지면 생략")
            @RequestParam(required = false) String cursorValue,
            @Parameter(description = "직전 응답의 nextCursorId. 첫 페이지면 생략")
            @RequestParam(required = false) @Positive Long cursorId,
            @Parameter(description = "페이지 크기(최대 100)")
            @RequestParam(defaultValue = "50") @Positive @Max(100) int size
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.getMessages(accountId, roomId, cursorValue, cursorId, size)));
    }

    @Operation(summary = "텍스트 메시지 발송", description = "REST 폴백 경로로 텍스트 메시지를 발송합니다.")
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> sendMessage(
            @PathVariable @Positive Long roomId,
            @RequestBody @Valid ChatMessageSendRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.sendMessage(accountId, roomId, request)));
    }

    @Operation(summary = "이미지 메시지 발송", description = "S3 업로드 완료된 이미지 URL로 이미지 메시지를 발송합니다.")
    @PostMapping("/rooms/{roomId}/messages/image")
    public ResponseEntity<ApiResponse<ChatMessageResponseDto>> sendImageMessage(
            @PathVariable @Positive Long roomId,
            @RequestBody @Valid ChatImageMessageSendRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.sendImageMessage(accountId, roomId, request)));
    }

    @Operation(summary = "메시지 삭제", description = "본인이 발송한 메시지를 Soft Delete 합니다.")
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable @Positive Long messageId) {
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
