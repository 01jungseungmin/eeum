package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.QOrder;
import com.eeum.eeum.domain.order.entity.QOrderItem;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.product.entity.QEventProduct;
import com.eeum.eeum.domain.product.entity.QProduct;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QOrderItem orderItem = QOrderItem.orderItem;
    private final QOrder order = QOrder.order;
    private final QEventProduct eventProduct = QEventProduct.eventProduct;
    private final QProduct product = QProduct.product;

    @Override
    public List<ProductSalesQuantity> aggregateSoldQuantityByProduct(
            Long storeId,
            Collection<OrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    ) {
        NumberExpression<Long> soldQuantity =
                Expressions.numberTemplate(Long.class, "sum({0})", orderItem.quantity);

        return queryFactory
                .select(Projections.constructor(ProductSalesQuantity.class,
                        product.productId, product.name, product.productType, soldQuantity))
                .from(orderItem)
                .join(order).on(order.orderId.eq(orderItem.order.orderId))
                .leftJoin(eventProduct).on(eventProduct.eventProductId.eq(orderItem.eventProductId))
                .join(product).on(product.productId.eq(resolvedProductId()))
                .where(
                        order.store.storeId.eq(storeId),
                        order.status.in(statuses),
                        createdAtGoe(from),
                        createdAtLoe(to),
                        // 삭제된 상품 제외. ProductService.deleteProduct()가 행을 지우지 않고 INACTIVE로 내린다
                        product.status.ne(ProductStatus.INACTIVE)
                )
                .groupBy(product.productId, product.name, product.productType)
                .orderBy(soldQuantity.desc(), product.productId.asc())
                .fetch();
    }

    /**
     * 이벤트 상품 주문 행은 product_id가 비어 있고 event_product_id만 있다 —
     * 원본 상품 ID로 되돌려 같은 상품의 일반 판매분과 한 행으로 합친다.
     */
    private NumberExpression<Long> resolvedProductId() {
        return Expressions.numberTemplate(Long.class, "coalesce({0}, {1})",
                orderItem.productId, eventProduct.product.productId);
    }

    private BooleanExpression createdAtGoe(LocalDateTime from) {
        return from != null ? order.createdAt.goe(from) : null;
    }

    private BooleanExpression createdAtLoe(LocalDateTime to) {
        return to != null ? order.createdAt.loe(to) : null;
    }
}
