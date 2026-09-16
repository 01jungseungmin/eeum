package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
class UnreadSnapshotLoader {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public UnreadSnapshot load(Long accountId) {
        long total = notificationRepository.countByAccount_AccountIdAndIsReadFalse(accountId);
        Map<NotificationCategory, Long> categories = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            categories.put(category, 0L);
        }
        categories.putAll(notificationRepository.countUnreadByCategory(accountId));
        return new UnreadSnapshot(total, categories);
    }

    record UnreadSnapshot(long total, Map<NotificationCategory, Long> categoryCounts) {
    }
}
