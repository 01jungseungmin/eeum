package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 중고 게시글 공개 노출 조건을 한 곳에 모아둔다.
 * <p>
 * 삭제·숨김·판매자 탈퇴 중 하나라도 해당하면 어디에서도 보이면 안 된다.
 * 같은 조건이 목록·상세·찜·신고에 따로 적혀 있으면 한쪽만 고쳤을 때 우회 경로가 생긴다.
 * 조건이 바뀌면 여기와 {@code UsedProduct#isPubliclyVisible}을 함께 고친다.
 */
public final class UsedProductVisibilityPredicate {

    private UsedProductVisibilityPredicate() {
    }

    /**
     * 미삭제 + 미숨김 + 판매자 활성.
     *
     * @param product 대상 게시글 경로
     * @param seller  {@code product.seller}로 조인한 별칭 (묵시적 조인을 만들지 않기 위해 받는다)
     */
    public static BooleanExpression publiclyVisible(QUsedProduct product, QAccount seller) {
        return product.deletedAt.isNull()
                .and(product.hidden.isFalse())
                .and(seller.status.eq(AccountStatus.ACTIVE));
    }
}
