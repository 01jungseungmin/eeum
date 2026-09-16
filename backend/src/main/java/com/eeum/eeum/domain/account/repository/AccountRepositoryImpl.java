package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.enums.AccountRole;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QAccount account = QAccount.account;

    @Override
    public Page<Account> searchAccounts(
            AccountStatus status,
            AccountRole role,
            String keyword,
            Pageable pageable
    ) {
        List<Account> content = queryFactory
                .selectFrom(account)
                .where(
                        statusEq(status),
                        roleEq(role),
                        keywordContains(keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(resolveSort(pageable))
                .fetch();

        Long total = queryFactory
                .select(account.count())
                .from(account)
                .where(
                        statusEq(status),
                        roleEq(role),
                        keywordContains(keyword)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(AccountStatus status) {
        return status == null
                ? null
                : account.status.eq(status);
    }

    private BooleanExpression roleEq(AccountRole role) {
        return role == null
                ? null
                : account.role.eq(role);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String trimmedKeyword = keyword.trim();

        return account.email.containsIgnoreCase(trimmedKeyword)
                .or(account.nickname.containsIgnoreCase(trimmedKeyword))
                .or(account.name.containsIgnoreCase(trimmedKeyword));
    }

    private OrderSpecifier<?> resolveSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return account.createdAt.desc();
        }

        for (Sort.Order order : pageable.getSort()) {
            boolean asc = order.isAscending();

            return switch (order.getProperty()) {
                case "createdAt" -> asc
                        ? account.createdAt.asc()
                        : account.createdAt.desc();

                case "modifiedAt" -> asc
                        ? account.modifiedAt.asc()
                        : account.modifiedAt.desc();

                case "email" -> asc
                        ? account.email.asc()
                        : account.email.desc();

                case "nickname" -> asc
                        ? account.nickname.asc()
                        : account.nickname.desc();

                case "name" -> asc
                        ? account.name.asc()
                        : account.name.desc();

                case "status" -> asc
                        ? account.status.asc()
                        : account.status.desc();

                case "role" -> asc
                        ? account.role.asc()
                        : account.role.desc();

                default -> account.createdAt.desc();
            };
        }

        return account.createdAt.desc();
    }
}