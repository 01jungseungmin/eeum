package com.eeum.eeum.domain.report.event;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;

public record ReportActionNotificationEvent(
        Long targetAccountId,
        NotificationRefType refType,
        Long refId,
        String actionLabel,
        String adminNote
) {}
