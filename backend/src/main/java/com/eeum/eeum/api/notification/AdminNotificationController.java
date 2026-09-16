package com.eeum.eeum.api.notification;

import com.eeum.eeum.application.notification.dto.request.EventNoticeRequestDto;
import com.eeum.eeum.application.notification.dto.request.SystemNoticeRequestDto;
import com.eeum.eeum.application.notification.dto.request.NotificationAdminSearchDto;
import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.application.notification.service.AdminNotificationService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "01. Admin - Notification", description = "관리자 알림 발송 / 이력 조회 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @Operation(summary = "시스템 공지 발송",
            description = "전체 또는 특정 계정에 시스템 공지를 발송합니다.비동기 처리(@Async)로 즉시 200 응답 후 백그라운드에서 발송합니다.")
    @PostMapping("/system")
    public ResponseEntity<ApiResponse<Void>> sendSystemNotice(
            @RequestBody @Valid SystemNoticeRequestDto request
    ) {
        adminNotificationService.sendSystemNotice(request);
        return ResponseEntity.ok(ApiResponse.success(null, "시스템 공지 발송이 시작되었습니다."));
    }

    @Operation(
            summary = "이벤트/마케팅 알림 발송",
            description = "마케팅 수신 동의자에게만 이벤트 알림을 발송합니다. 비동기 처리."
    )
    @PostMapping("/event")
    public ResponseEntity<ApiResponse<Void>> sendEventNotice(
            @RequestBody @Valid EventNoticeRequestDto request
    ) {
        adminNotificationService.sendEventNotice(request);
        return ResponseEntity.ok(ApiResponse.success(null, "이벤트 알림 발송이 시작되었습니다."));
    }

    @Operation(summary = "발송 이력 조회", description = "type / 기간 / 수신자로 필터링하여 발송 이력을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getHistory(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        NotificationAdminSearchDto condition = NotificationAdminSearchDto.builder()
                .type(type)
                .accountId(accountId)
                .from(from)
                .to(to)
                .build();
        return ResponseEntity.ok(ApiResponse.success(
                adminNotificationService.getNotificationHistory(condition, pageable)));
    }
}
