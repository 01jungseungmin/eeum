package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.application.chat.dto.request.ParticipantInviteRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomDetailResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomPublicResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.service.ChatRoomService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "15. Chat Room", description = "채팅방 API (그룹/단톡방)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(summary = "그룹(단톡방) 채팅방 생성", description = "GROUP/GROUP_STREET 채팅방을 생성하고 참여자를 일괄 초대합니다.")
    @PostMapping("/group")
    public ResponseEntity<ApiResponse<ChatRoomResponseDto>> createGroupRoom(
            @RequestBody @Valid GroupChatRoomCreateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.createGroupRoom(accountId, request)));
    }

    @Operation(
            summary = "중고거래 문의 채팅방 시작",
            description = """
                    중고 게시글의 판매자와 1:1 문의 채팅방을 생성하거나 기존 활성 문의방을 반환합니다.
                    동일 게시글에 대한 활성 문의방이 이미 존재하는 경우 새로 생성하지 않습니다.
                    본인 게시글 및 삭제·숨김 처리된 게시글에는 문의할 수 없습니다.
                    문의 시작을 위해 GPS 인증된 활동 지역이 필요합니다.
                    """
    )
    @PostMapping("/used-products/{usedProductId}")
    public ResponseEntity<ApiResponse<ChatRoomResponseDto>> createUsedProductInquiry(
            @PathVariable @Positive Long usedProductId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.createUsedProductInquiry(accountId, usedProductId)));
    }

    @Operation(
            summary = "내 채팅방 목록",
            description = "마지막 메시지 시각 내림차순으로 정렬되며 안 읽은 메시지 수를 포함합니다. 무한 스크롤 지원. " +
                    "기본은 활성 채팅방만 반환하며, includeClosed=true를 주면 종료된 방까지 포함해 지난 대화를 열람할 수 있습니다 " +
                    "(응답의 active 필드로 구분)."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Slice<ChatRoomResponseDto>>> getMyRooms(
            @Parameter(description = "종료된 채팅방 포함 여부 (기본 false)")
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.getMyRooms(accountId, pageable, includeClosed)));
    }

    @Operation(
            summary = "지역 공개 채팅방 탐색",
            description = "내 인증 지역과 동일한 지역의 활성 GROUP/GROUP_STREET 채팅방을 조회합니다. " +
                    "joined=true이면 이미 입장한 방이며, false이면 입장 버튼을 통해 참여할 수 있습니다. " +
                    "주요 지역이 설정되지 않은 경우 404를 반환합니다. 무한 스크롤 지원."
    )
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Slice<ChatRoomPublicResponseDto>>> getPublicRooms(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.getPublicRooms(accountId, pageable)));
    }

    @Operation(summary = "채팅방 상세", description = "참여자(ACTIVE) 목록을 포함한 채팅방 상세를 조회합니다.")
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponseDto>> getRoomDetail(
            @PathVariable Long roomId
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.getRoomDetail(accountId, roomId)));
    }

    @Operation(
            summary = "채팅방 직접 입장",
            description = "초대 없이 일반 GROUP 및 공개된 STORE 채팅방에 스스로 참여합니다. " +
                    "사용자의 현재 인증된 대표 지역과 채팅방 지역이 같아야 합니다. " +
                    "이미 ACTIVE 참여자인 경우 읽음 처리만 수행하며 200을 반환합니다. " +
                    "신규 참여 / 재입장 모두 동일하게 200을 반환합니다."
    )
    @PostMapping("/{roomId}/participants/me")
    public ResponseEntity<ApiResponse<Void>> joinRoom(@PathVariable Long roomId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatRoomService.joinRoom(accountId, roomId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "채팅방 참여자 초대", description = "GROUP 채팅방에 참여자를 초대하고 입장 시스템 메시지를 남깁니다.")
    @PostMapping("/{roomId}/participants")
    public ResponseEntity<ApiResponse<Void>> inviteParticipants(
            @PathVariable Long roomId,
            @RequestBody @Valid ParticipantInviteRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatRoomService.inviteParticipants(accountId, roomId, request.getAccountIds());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "채팅방 나가기",
            description = "GROUP은 참여 상태를 LEFT로 바꾸고, 마지막 참여자가 나가면 채팅방이 종료됩니다. "
                    + "PRIVATE 문의방은 참여자가 둘뿐이라 한 명만 나가도 채팅방 전체가 종료되며, "
                    + "참여 상태는 유지되어 양쪽 모두 지난 대화를 계속 열람할 수 있습니다(목록 조회 시 includeClosed=true). "
                    + "이미 종료된 문의방에서는 다시 나갈 수 없습니다.")
    @PatchMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(@PathVariable Long roomId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatRoomService.leaveRoom(accountId, roomId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(
            summary = "채팅방 종료(폭파)",
            description = "채팅방을 종료합니다. 물리 삭제가 아닌 상태 전이(active=false)이며 대화 기록은 보존됩니다. " +
                    "종료 후에는 메시지 발송과 WebSocket 구독이 차단되고 참여자의 채팅방 목록에서 제외됩니다. " +
                    "가게 단톡방은 해당 가게 사장만, 그 외 그룹 방은 생성자만 종료할 수 있습니다. " +
                    "이미 종료된 방에 대한 재요청도 200을 반환합니다(멱등)."
    )
    @PatchMapping("/{roomId}/close")
    public ResponseEntity<ApiResponse<Void>> closeRoom(@PathVariable Long roomId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatRoomService.closeRoom(accountId, roomId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "읽음 처리", description = "lastReadTime을 갱신하고 해당 방의 안 읽음 카운트를 0으로 리셋합니다.")
    @PatchMapping("/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markRoomAsRead(@PathVariable Long roomId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        chatRoomService.markRoomAsRead(accountId, roomId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
