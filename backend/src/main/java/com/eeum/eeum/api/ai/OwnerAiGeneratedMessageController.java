package com.eeum.eeum.api.ai;

import com.eeum.eeum.application.ai.dto.request.AiGeneratedMessageUpdateRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiMessageScheduleRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.service.AiGeneratedMessageService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "10. Owner AI Manager", description = "사장 웹 AI 매니저 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/owner/ai-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiGeneratedMessageController {

    private final AiGeneratedMessageService aiGeneratedMessageService;

    @Operation(summary = "AI 생성 메시지 목록 조회", description = "type 파라미터로 메시지 유형 필터링이 가능합니다.")
    @GetMapping("/generated-messages")
    public ResponseEntity<ApiResponse<Page<AiGeneratedMessageResponseDto>>> getMessages(
            @Parameter(description = "메시지 유형 필터 (선택)") @RequestParam(required = false) AiMessageType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.getMessages(ownerId, type, pageable)));
    }

    @Operation(summary = "AI 생성 메시지 상세 조회")
    @GetMapping("/generated-messages/{messageId}")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> getMessage(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.getMessage(ownerId, messageId)));
    }

    @Operation(summary = "AI 생성 메시지 수정",
            description = "초안/검토/예약 상태의 메시지만 수정 가능합니다. 수정 시 REVIEWED 상태가 됩니다.")
    @PatchMapping("/generated-messages/{messageId}")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> updateMessage(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId,
            @Valid @RequestBody AiGeneratedMessageUpdateRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.updateMessage(ownerId, messageId, request)));
    }

    @Operation(summary = "검토 후 보내기",
            description = "1차에서는 실제 외부 발송 없이 상태를 SENT로 변경하고 발송 이벤트를 발행합니다. 중복 클릭은 1회만 처리됩니다.")
    @PostMapping("/generated-messages/{messageId}/send")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> sendMessage(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.sendMessage(ownerId, messageId)));
    }

    @Operation(summary = "예약 발송", description = "예약 시각이 현재보다 과거면 AI_INVALID_SCHEDULE_TIME 예외가 발생합니다.")
    @PostMapping("/generated-messages/{messageId}/schedule")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> scheduleMessage(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId,
            @Valid @RequestBody AiMessageScheduleRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.scheduleMessage(ownerId, messageId, request)));
    }

    @Operation(summary = "메시지 취소", description = "발송 전 메시지를 취소합니다. 이미 발송된 메시지는 취소할 수 없습니다.")
    @PostMapping("/generated-messages/{messageId}/cancel")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> cancelMessage(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.cancelMessage(ownerId, messageId)));
    }

    @Operation(summary = "공지 등록",
            description = "공지 초안을 등록 처리합니다. STORE_NOTICE 채널이면 실제 상점 공지가 생성됩니다. SNS_CARD 채널은 발송 불가입니다.")
    @PostMapping("/notices/{messageId}/publish")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> publishNotice(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.publishNotice(ownerId, messageId)));
    }

    @Operation(summary = "공지 예약 등록",
            description = "공지 초안을 예약 등록합니다. 예약 시각이 과거면 AI_INVALID_SCHEDULE_TIME 예외가 발생합니다.")
    @PostMapping("/notices/{messageId}/schedule")
    public ResponseEntity<ApiResponse<AiGeneratedMessageResponseDto>> scheduleNotice(
            @Parameter(description = "메시지 ID") @PathVariable Long messageId,
            @Valid @RequestBody AiMessageScheduleRequestDto request
    ) {
        Long ownerId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(aiGeneratedMessageService.scheduleNotice(ownerId, messageId, request)));
    }
}
