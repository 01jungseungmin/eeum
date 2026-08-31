package com.eeum.eeum.domain.store.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.entity.QOwnerInfo;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.enums.ApprovalStatus;
import com.eeum.eeum.domain.store.entity.QStore;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

/**
 * 상점 공개 노출 조건을 한 곳에 모아둔다.
 * <p>
 * 같은 규칙이 상점 상세({@code PublicStoreService.validatePublicVisibleStore})와 찜 경로에
 * 따로 적혀 있으면 한쪽만 고쳤을 때 우회 경로가 생긴다. 조건이 바뀌면 여기만 고친다.
 */
public final class StoreVisibilityPredicate {

    private StoreVisibilityPredicate() {
    }

    /**
     * 계정 활성 + 상점 미정지 + 사장 승인 완료.
     *
     * @param store   대상 상점 경로
     * @param account {@code store.account}로 조인한 별칭 (묵시적 조인을 만들지 않기 위해 받는다)
     */
    public static BooleanExpression publiclyVisible(QStore store, QAccount account) {
        QOwnerInfo ownerInfo = QOwnerInfo.ownerInfo;

        return account.status.eq(AccountStatus.ACTIVE)
                .and(store.status.ne(StoreStatus.SUSPENDED))
                .and(JPAExpressions.selectOne()
                        .from(ownerInfo)
                        .where(
                                ownerInfo.account.accountId.eq(account.accountId),
                                ownerInfo.approvalStatus.eq(ApprovalStatus.APPROVED))
                        .exists());
    }
}
