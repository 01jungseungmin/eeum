package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.application.notification.dto.request.NotificationAdminSearchDto;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface NotificationRepositoryCustom {

    //안 읽은 알림 수를 카테고리별로 집계 (웹 배지 — 탭별 카운트)
    Map<NotificationCategory, Long> countUnreadByCategory(Long accountId);

    // 여러 계정의 안 읽은 알림 수를 한 번에 집계 (캐시 정합성 보정용).
    // 계정마다 따로 세면 캐시가 살아 있는 계정 수만큼 쿼리가 나간다.
    // 미읽음이 0인 계정은 결과에 없다 — 호출부가 0으로 간주한다.
    Map<Long, Long> countUnreadByAccountIds(Collection<Long> accountIds);

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
