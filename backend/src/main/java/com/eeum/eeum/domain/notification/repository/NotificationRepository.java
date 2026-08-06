package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    Optional<Notification> findByNotificationIdAndAccount_AccountId(
            Long notificationId, Long accountId);

    Page<Notification> findAllByAccount_AccountIdOrderByCreatedAtDesc(
            Long accountId, Pageable pageable);

    Page<Notification> findAllByAccount_AccountIdAndIsReadFalseOrderByCreatedAtDesc(
            Long accountId, Pageable pageable);

    // 카테고리 필터 — UI 탭별 조회
    @Query("""
        SELECT n FROM Notification n
        WHERE n.account.accountId = :accountId
          AND n.type IN :types
        ORDER BY n.createdAt DESC
        """)
    Page<Notification> findAllByAccountIdAndTypeIn(
            @Param("accountId") Long accountId,
            @Param("types") List<NotificationType> types,
            Pageable pageable
    );

    // 안 읽은 알림 수 — Redis 캐시 미스 시 DB fallback
    long countByAccount_AccountIdAndIsReadFalse(Long accountId);

    // 일괄 읽음 처리 (isRead=true, readAt=now)
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.isRead = true, n.readAt = :now
        WHERE n.account.accountId = :accountId
          AND n.isRead = false
        """)
    int markAllAsReadByAccountId(
            @Param("accountId") Long accountId,
            @Param("now") LocalDateTime now
    );

    // 참조 대상 기준 일괄 읽음 처리 (예: 채팅방 읽음 시 해당 방의 CHAT_MESSAGE 알림 동기화)
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.isRead = true, n.readAt = :now
        WHERE n.account.accountId = :accountId
          AND n.type = :type
          AND n.refType = :refType
          AND n.refId = :refId
          AND n.isRead = false
        """)
    int markAsReadByAccountAndTypeAndRef(
            @Param("accountId") Long accountId,
            @Param("type") NotificationType type,
            @Param("refType") NotificationRefType refType,
            @Param("refId") Long refId,
            @Param("now") LocalDateTime now
    );

    // 회원 탈퇴 CASCADE
    void deleteAllByAccount_AccountId(Long accountId);

    // 대상 도메인 삭제 시 연관 알림 CASCADE
    void deleteAllByRefTypeAndRefId(NotificationRefType refType, Long refId);

    // 좋아요 알림 중복 방지 — 동일 (수신자, 타입, 참조) 알림이 이미 존재하면 skip
    boolean existsByAccount_AccountIdAndTypeAndRefTypeAndRefId(
            Long accountId, NotificationType type, NotificationRefType refType, Long refId);

    // 여러 사용자의 동일 참조 알림 일괄 읽음 처리 (채팅방 종료 시 참여자 전원 동기화)
    // 참여자 수만큼 UPDATE를 반복하지 않도록 IN 절 한 번으로 처리한다
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.isRead = true, n.readAt = :now
        WHERE n.account.accountId IN :accountIds
          AND n.type = :type
          AND n.refType = :refType
          AND n.refId = :refId
          AND n.isRead = false
        """)
    int markAsReadByAccountsAndTypeAndRef(
            @Param("accountIds") List<Long> accountIds,
            @Param("type") NotificationType type,
            @Param("refType") NotificationRefType refType,
            @Param("refId") Long refId,
            @Param("now") LocalDateTime now
    );

    // 오래된 알림 정리 배치 (6개월 이전)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    int deleteOldNotifications(@Param("threshold") LocalDateTime threshold);
}
