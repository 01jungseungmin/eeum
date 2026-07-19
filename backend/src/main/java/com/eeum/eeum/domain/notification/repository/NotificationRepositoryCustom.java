package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.application.notification.dto.request.NotificationAdminSearchDto;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

public interface NotificationRepositoryCustom {

    //안 읽은 알림 수를 카테고리별로 집계 (웹 배지 — 탭별 카운트)
    Map<NotificationCategory, Long> countUnreadByCategory(Long accountId);

    //관리자용 발송 이력 검색 (type / 기간 / 수신자 필터).
    Page<Notification> searchAdminNotifications(
            NotificationAdminSearchDto condition,
            Pageable pageable
    );

    //통계 — 기간별 type 발송 수.
    long countSentByTypeAndPeriod(
            NotificationType type,
            LocalDateTime from,
            LocalDateTime to
    );
}
