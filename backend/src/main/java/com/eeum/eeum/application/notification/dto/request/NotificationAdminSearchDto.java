package com.eeum.eeum.application.notification.dto.request;

import com.eeum.eeum.domain.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class NotificationAdminSearchDto {

    // 알림 타입 필터 (null이면 전체)
    private NotificationType type;

    // 수신자 accountId 필터 (null이면 전체)
    private Long accountId;

    // 발송 시작 일시 (null이면 무제한)
    private LocalDateTime from;

    //발송 종료 일시 (null이면 무제한)
    private LocalDateTime to;
}
