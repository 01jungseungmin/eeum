package com.eeum.eeum.api.notification;

import com.eeum.eeum.application.notification.dto.response.NotificationResponseDto;
import com.eeum.eeum.application.notification.dto.response.NotificationSettingsResponseDto;
import com.eeum.eeum.application.notification.dto.request.NotificationSettingsUpdateRequestDto;
import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.application.notification.service.NotificationSettingsService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "14. Notification", description = "알림 / 알림 설정 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingsService settingsService;

    // ===================== SSE 실시간 구독 =====================

     //SSE(Server-Sent Events)
     //웹 클라이언트(사장님/관리자 대시보드)에서 한 번 연결하면 새 알림이 생성될 때마다 서버가 unread-count 이벤트를 자동으로 push
     //클라이언트는 연결이 끊기면 EventSource API가 자동으로 재연결한다.
    @Operation(
            summary = "SSE 실시간 알림 구독",
            description = "웹 사장님/관리자 대시보드용 실시간 배지 카운트 스트림. " +
                    "연결 즉시 현재 unread 카운트를 전송하고, 이후 변경될 때마다 push한다."
    )
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return notificationService.subscribe(accountId);
    }

    // ===================== 알림 목록 조회 =====================

    @Operation(
            summary = "내 알림 목록 (카테고리 필터)",
            description = "최신순으로 알림 목록을 페이징 조회합니다. " +
                    "category 파라미터로 탭 필터링이 가능합니다 (미전달 시 전체 조회)."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getMyNotifications(
            @Parameter(description = "카테고리 필터 (ORDER·CHAT·REVIEW·RESERVATION·PRODUCT·SYSTEM)")
            @RequestParam(required = false) NotificationCategory category,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyNotificationsByCategory(accountId, category, pageable)));
    }

    @Operation(summary = "안 읽은 알림 목록", description = "읽지 않은 알림만 최신순으로 조회합니다.")
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getUnreadNotifications(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyUnreadNotifications(accountId, pageable)));
    }

    @Operation(
            summary = "안 읽은 알림 수 (앱/웹 배지)",
            description = "Redis 캐시에서 빠르게 반환합니다. 캐시 미스 시 DB fallback. " +
                    "웹에서는 SSE 구독을 사용하면 폴링 없이 실시간으로 수신할 수 있습니다."
    )
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<UnreadCountResponseDto>> getUnreadCount() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadCount(accountId)));
    }

    // ===================== 읽음 처리 =====================

    @Operation(summary = "알림 단건 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long notificationId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        notificationService.markAsRead(accountId, notificationId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "전체 읽음 처리", description = "모든 안 읽은 알림을 읽음 처리합니다.")
    @PatchMapping("/read/all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        notificationService.markAllAsRead(accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 삭제 =====================

    @Operation(summary = "알림 단건 삭제")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long notificationId) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        notificationService.deleteNotification(accountId, notificationId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "알림 전체 삭제 (알림함 비우기)")
    @DeleteMapping("/all")
    public ResponseEntity<ApiResponse<Void>> deleteAllNotifications() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        notificationService.deleteAllByAccountId(accountId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ===================== 알림 설정 =====================

    @Operation(summary = "내 알림 수신 설정 조회")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponseDto>> getSettings() {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                settingsService.getMySettings(accountId)));
    }

    @Operation(
            summary = "알림 수신 설정 변경",
            description = "카테고리별 ON/OFF, DND, 이메일, 알림음 설정을 변경합니다. " +
                    "- 주문/예약/시스템 알림은 필수이므로 변경해도 서버에서 무시합니다. " +
                    "- marketingEnabled=true 설정 시 marketingAgreedAt이 자동 기록됩니다 (법적 증빙). " +
                    "- null 필드는 변경하지 않습니다 (부분 업데이트)."
    )
    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponseDto>> updateSettings(
            @RequestBody @Valid NotificationSettingsUpdateRequestDto request
    ) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        return ResponseEntity.ok(ApiResponse.success(
                settingsService.updateSettings(accountId, request)));
    }
}
