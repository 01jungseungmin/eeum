package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.application.store.dto.request.NearbyStoreSearchCondition;
import com.eeum.eeum.application.store.dto.request.StoreSearchDto;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.entity.QOwnerInfo;
import com.eeum.eeum.domain.store.entity.QStore;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QStore store = QStore.store;
    private final QAccount account = QAccount.account;
    private final QOwnerInfo ownerInfo = QOwnerInfo.ownerInfo;

    @Override
    public Page<Store> searchStores(StoreSearchDto condition, Pageable pageable) {
        List<Store> content = queryFactory
                .selectFrom(store)
                .join(store.account, account)
                .join(ownerInfo).on(ownerInfo.account.eq(account))
                .where(
                        publicVisibleStore(),
                        categoryEq(condition.getCategoryId()),
                        regionEq(condition.getRegionId()),
                        keywordContains(condition.getKeyword())
                )
                .orderBy(storeStatusPriority(), store.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(store.count())
                .from(store)
                .join(store.account, account)
                .join(ownerInfo).on(ownerInfo.account.eq(account))
                .where(
                        publicVisibleStore(),
                        categoryEq(condition.getCategoryId()),
                        regionEq(condition.getRegionId()),
                        keywordContains(condition.getKeyword())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<Store> findNearbyStoresWithFilter(NearbyStoreSearchCondition condition) {
        NumberTemplate<Double> distance = Expressions.numberTemplate(
                Double.class,
                """
                (6371 * acos(
                    least(1, greatest(-1,
                        cos(radians({0})) * cos(radians({1})) *
                        cos(radians({2}) - radians({3})) +
                        sin(radians({0})) * sin(radians({1}))
                    ))
                ))
                """,
                condition.getLatitude(),
                store.latitude,
                store.longitude,
                condition.getLongitude()
        );

        return queryFactory
                .selectFrom(store)
                .join(store.account, account)
                .join(ownerInfo).on(ownerInfo.account.eq(account))
                .where(
                        publicVisibleStore(),
                        store.latitude.isNotNull(),
                        store.longitude.isNotNull(),
                        categoryEq(condition.getCategoryId()),
                        regionEq(condition.getRegionId()),
                        keywordContains(condition.getKeyword()),
                        distance.loe(condition.getRadiusKm())
                )
                .orderBy(distance.asc())
                .fetch();
    }

    private BooleanExpression publicVisibleStore() {
        return account.status.eq(AccountStatus.ACTIVE)
                .and(ownerInfo.approvalStatus.eq(ApprovalStatus.APPROVED))
                .and(store.status.ne(StoreStatus.SUSPENDED));
    }

    private BooleanExpression categoryEq(Long categoryId) {
        return categoryId == null ? null : store.category.categoryId.eq(categoryId);
    }

    private BooleanExpression regionEq(Long regionId) {
        return regionId == null ? null : store.region.regionId.eq(regionId);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return store.name.containsIgnoreCase(keyword)
                .or(store.address.containsIgnoreCase(keyword));
    }

    private OrderSpecifier<Integer> storeStatusPriority() {
        return Expressions.numberTemplate(
                Integer.class,
                """
                case
                    when {0} = 'OPEN' then 0
                    when {0} = 'TEMP_CLOSED' then 1
                    when {0} = 'CLOSED' then 2
                    else 3
                end
                """,
                store.status.stringValue()
        ).asc();
    }
}