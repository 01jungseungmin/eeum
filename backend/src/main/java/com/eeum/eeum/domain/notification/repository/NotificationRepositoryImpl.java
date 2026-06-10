package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.application.notification.dto.request.NotificationAdminSearchDto;
import com.eeum.eeum.domain.notification.entity.Notification;
import com.eeum.eeum.domain.notification.entity.QNotification;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QNotification notification = QNotification.notification;

    @Override
    public Page<Notification> searchAdminNotifications(
            NotificationAdminSearchDto condition,
            Pageable pageable
    ) {
        List<Notification> content = queryFactory
                .selectFrom(notification)
                .where(
                        typeEq(condition.getType()),
                        accountEq(condition.getAccountId()),
                        createdAfter(condition.getFrom()),
                        createdBefore(condition.getTo())
                )
                .orderBy(notification.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(notification.count())
                .from(notification)
                .where(
                        typeEq(condition.getType()),
                        accountEq(condition.getAccountId()),
                        createdAfter(condition.getFrom()),
                        createdBefore(condition.getTo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long countSentByTypeAndPeriod(
            NotificationType type,
            LocalDateTime from,
            LocalDateTime to
    ) {
        Long count = queryFactory
                .select(notification.count())
                .from(notification)
                .where(
                        typeEq(type),
                        createdAfter(from),
                        createdBefore(to)
                )
                .fetchOne();
        return count != null ? count : 0L;
    }
    // 조건 빌더

    private BooleanExpression typeEq(NotificationType type) {
        return type != null ? notification.type.eq(type) : null;
    }

    private BooleanExpression accountEq(Long accountId) {
        return accountId != null ? notification.account.accountId.eq(accountId) : null;
    }

    private BooleanExpression createdAfter(LocalDateTime from) {
        return from != null ? notification.createdAt.goe(from) : null;
    }

    private BooleanExpression createdBefore(LocalDateTime to) {
        return to != null ? notification.createdAt.loe(to) : null;
    }
}
