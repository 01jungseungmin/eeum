package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.application.account.dto.request.OwnerInfoSearchDto;
import com.eeum.eeum.application.account.dto.response.OwnerApplicationListResponseDto;
import com.eeum.eeum.domain.account.entity.QOwnerInfo;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.store.entity.QStore;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OwnerInfoRepositoryImpl implements OwnerInfoRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QOwnerInfo ownerInfo = QOwnerInfo.ownerInfo;
    private final QStore store = QStore.store;

    @Override
    public Page<OwnerApplicationListResponseDto> searchOwnerApplications(
            OwnerInfoSearchDto condition,
            Pageable pageable
    ) {
        List<OwnerApplicationListResponseDto> content = queryFactory
                .select(Projections.constructor(
                        OwnerApplicationListResponseDto.class,
                        ownerInfo.ownerInfoId,
                        ownerInfo.account.accountId,
                        ownerInfo.account.email,
                        ownerInfo.account.name,
                        ownerInfo.account.phone,
                        ownerInfo.businessNumber,
                        ownerInfo.openingDate,
                        ownerInfo.approvalStatus.stringValue(),
                        ownerInfo.reviewRequestedAt,
                        store.storeId,
                        store.name,
                        store.address,
                        store.status.stringValue(),
                        ownerInfo.createdAt
                ))
                .from(ownerInfo)
                .leftJoin(ownerInfo.account)
                .leftJoin(store).on(store.account.eq(ownerInfo.account))
                .where(
                        reviewRequested(),
                        approvalStatusEq(condition.getApprovalStatus()),
                        businessNumberContains(condition.getBusinessNumber()),
                        storeNameContains(condition.getStoreName()),
                        requestedFromGoe(condition.getRequestedFrom()),
                        requestedToLoe(condition.getRequestedTo())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(resolveSort(pageable))
                .fetch();

        Long total = queryFactory
                .select(ownerInfo.count())
                .from(ownerInfo)
                .leftJoin(store).on(store.account.eq(ownerInfo.account))
                .where(
                        reviewRequested(),
                        approvalStatusEq(condition.getApprovalStatus()),
                        businessNumberContains(condition.getBusinessNumber()),
                        storeNameContains(condition.getStoreName()),
                        requestedFromGoe(condition.getRequestedFrom()),
                        requestedToLoe(condition.getRequestedTo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression reviewRequested() {
        return ownerInfo.reviewRequestedAt.isNotNull();
    }

    private BooleanExpression approvalStatusEq(ApprovalStatus approvalStatus) {
        return approvalStatus == null
                ? null
                : ownerInfo.approvalStatus.eq(approvalStatus);
    }

    private BooleanExpression businessNumberContains(String businessNumber) {
        return businessNumber == null || businessNumber.isBlank()
                ? null
                : ownerInfo.businessNumber.contains(businessNumber);
    }

    private BooleanExpression storeNameContains(String storeName) {
        return storeName == null || storeName.isBlank()
                ? null
                : store.name.containsIgnoreCase(storeName);
    }

    private BooleanExpression requestedFromGoe(LocalDate from) {
        return from == null
                ? null
                : ownerInfo.reviewRequestedAt.goe(from.atStartOfDay());
    }

    private BooleanExpression requestedToLoe(LocalDate to) {
        return to == null
                ? null
                : ownerInfo.reviewRequestedAt.loe(to.atTime(LocalTime.MAX));
    }

    private OrderSpecifier<?> resolveSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return ownerInfo.reviewRequestedAt.desc();
        }

        for (Sort.Order order : pageable.getSort()) {
            boolean asc = order.isAscending();

            return switch (order.getProperty()) {
                case "reviewRequestedAt" -> asc
                        ? ownerInfo.reviewRequestedAt.asc()
                        : ownerInfo.reviewRequestedAt.desc();

                case "createdAt" -> asc
                        ? ownerInfo.createdAt.asc()
                        : ownerInfo.createdAt.desc();

                case "approvalStatus" -> asc
                        ? ownerInfo.approvalStatus.asc()
                        : ownerInfo.approvalStatus.desc();

                default -> ownerInfo.reviewRequestedAt.desc();
            };
        }

        return ownerInfo.reviewRequestedAt.desc();
    }
}