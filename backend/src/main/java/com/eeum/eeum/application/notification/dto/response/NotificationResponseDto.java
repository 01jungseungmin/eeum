package com.eeum.eeum.application.notification.dto.response;

import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "알림 응답")
public class NotificationResponseDto {

    @Schema(description = "알림 ID", example = "1")
    private Long notificationId;

    @Schema(description = "알림 타입", example = "ORDER_STATUS_CHANGED")
    private NotificationType type;

    @Schema(description = "알림 제목", example = "주문이 확정되었습니다")
    private String title;

    @Schema(description = "알림 내용", example = "승민 반찬가게에서 주문을 확정했습니다.")
    private String content;

    @Schema(description = "참조 타입", example = "ORDER")
    private NotificationRefType refType;

    @Schema(description = "참조 ID (딥링크용)", example = "42")
    private Long refId;

    @Schema(description = "딥링크 URL", example = "/orders/42")
    private String linkUrl;

    @Schema(description = "읽음 여부", example = "false")
    private boolean isRead;

    @Schema(description = "읽은 시각")
    private LocalDateTime readAt;

    @Schema(description = "생성 시각")
    private LocalDateTime createdAt;

    public static NotificationResponseDto from(Notification n) {
        return NotificationResponseDto.builder()
                .notificationId(n.getNotificationId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .refType(n.getRefType())
                .refId(n.getRefId())
                .linkUrl(n.getLinkUrl())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
