package com.eeum.eeum.application.notification.dto.request;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

// 알림 생성 요청 DTO
@Getter
@Builder
public class NotificationCreateRequestDto {

    private Long accountId;
    private NotificationType type;
    private String title;
    private String content;
    private NotificationRefType refType;
    private Long refId;
    private String linkUrl;
}
