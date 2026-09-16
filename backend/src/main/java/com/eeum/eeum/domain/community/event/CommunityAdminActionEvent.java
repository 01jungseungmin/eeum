package com.eeum.eeum.domain.community.event;

import com.eeum.eeum.domain.notification.enums.NotificationRefType;

public record CommunityAdminActionEvent(
        Long targetAccountId,
        NotificationRefType refType,
        Long refId,
        String actionLabel,
        String adminNote
) {}
